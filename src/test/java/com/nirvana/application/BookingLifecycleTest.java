package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.config.RateLimitingProperties;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.security.RateLimitService;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.impl.BookingLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class BookingLifecycleTest {
    // Requests within a test share both limits. Other tests reuse the same customer and loopback IP.
    private static final ThreadLocal<String> rateLimitPrefix =
            ThreadLocal.withInitial(() -> "booking-test:" + UUID.randomUUID() + ":");

    @AfterEach
    void releaseRateLimitPrefix() {
        rateLimitPrefix.remove();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RateLimitFixture {
        @Bean
        @Primary
        RateLimitService bookingTestRateLimits(RateLimitingProperties properties, StringRedisTemplate redis,
                                               MeterRegistry metrics, com.nirvana.application.security.SharedRateWindow shared) {
            return new RateLimitService(properties, redis, metrics, shared) {
                @Override
                public boolean isAllowedSensitive(String key, int maxRequests) {
                    return super.isAllowedSensitive(rateLimitPrefix.get() + key, maxRequests);
                }
                @Override
                public boolean isAllowed(String key, int maxRequests) {
                    return super.isAllowed(rateLimitPrefix.get() + key, maxRequests);
                }
            };
        }
    }

    static final String SCHEDULE = "/api/owner/spas/1/schedule";
    static final ZoneId ZONE = ZoneId.of("Europe/London");
    final LocalDate day = LocalDate.now(ZONE).plusMonths(3).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired BookingLifecycleService lifecycle;
    @Autowired BookingRepository bookings;
    @Autowired com.nirvana.application.rewards.BookingWallet wallet;

    @Test void customerCanSpendCancellationCreditAndItsReturnIsIdempotent() throws Exception {
        long original = book(slot(10));
        var first = bookings.findById(original).orElseThrow();
        String currency = first.getCurrency();
        int before = jdbc.queryForObject("SELECT COALESCE(SUM(balance_cents),0) FROM booking_wallet_balance WHERE user_id=3 AND currency=?", Integer.class,currency);
        wallet.credit(first,currency,500);
        wallet.credit(first,currency,500);
        long nextSlot = slot(12);
        var stale = call(post("/api/bookings"),3,"ROLE_USER",Map.of("slotId",nextSlot,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","useWalletCredit",true,"expectedWalletCents",1));
        assertEquals(422,stale.status());
        var paid = call(post("/api/bookings"),3,"ROLE_USER",Map.of("slotId",nextSlot,"spaId",1,"serviceId",1,"guests",1,"paymentMode","OFFLINE","useWalletCredit",true));
        assertEquals(2,paid.status()/100,() -> paid.body().toString());
        long next = paid.body().get("bookingId").asLong();
        int spent = wallet.netSpent(next);
        assertTrue(spent >= 500);
        var booking = bookings.findById(next).orElseThrow();
        booking.setPolicySnapshotJson("{\"defaultFeePercent\":0}");
        sync();
        String url = "/api/bookings/" + next;
        var preview = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url + "/cancellation-quote"), 3, "ROLE_USER", null);
        assertEquals(200, preview.status());
        assertEquals(spent, preview.body().path("walletRefundCents").asInt());
        assertEquals(0, preview.body().path("walletFeeCents").asInt());
        var oldQuote = (com.fasterxml.jackson.databind.node.ObjectNode) preview.body().deepCopy();
        oldQuote.remove("walletRefundCents");
        assertEquals(409, call(post(url + "/cancel"), 3, "ROLE_USER", oldQuote).status());
        assertEquals(409, call(post(url + "/cancel"), 3, "ROLE_USER", null).status());
        assertEquals("CONFIRMED", status(next));
        var cancelled = call(post(url + "/cancel"), 3, "ROLE_USER", preview.body());
        assertEquals(200, cancelled.status(), () -> cancelled.body().toString());
        assertEquals(spent, cancelled.body().path("refundAmountCents").asInt());
        assertEquals(spent, cancelled.body().path("walletRefundCents").asInt());
        assertEquals(0, wallet.returnForBooking(booking,100));
        assertEquals(0, wallet.netSpent(next));
        assertEquals(before+500,wallet.quote(3,currency,Integer.MAX_VALUE));
    }

    @Test void supportRoutesKeepGuestAccountAndStaffConversationsPrivate() throws Exception {
        var created = mvc.perform(post("/api/public/support/cases").contentType("application/json")
                .content(json.writeValueAsString(Map.of("subject","Appointment question","message","How can I change the time?")))).andReturn().getResponse();
        assertEquals(201, created.getStatus());
        var token = json.readTree(created.getContentAsString());
        long id = token.get("caseId").asLong();
        String key = token.get("accessKey").asText();
        String staff = "/api/admin/support/cases/"+id;
        assertEquals(403, call(post(staff+"/replies"),3,"ROLE_USER", Map.of("message","Staff reply")).status());
        assertEquals(200, call(post(staff+"/replies"),1,"ROLE_PLATFORM_ADMIN",Map.of("message","We can help")).status());
        assertEquals(409,call(post(staff+"/assign"),2,"ROLE_ADMIN",null).status());
        assertEquals(409,call(post(staff+"/replies"),2,"ROLE_ADMIN",Map.of("message","Conflicting reply")).status());
        assertEquals(200,call(post(staff+"/assign?takeOverFrom=1"),2,"ROLE_ADMIN",null).status());
        assertEquals(409,call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(staff+"/state?state=CLOSED"),1,"ROLE_PLATFORM_ADMIN",null).status());
        assertEquals(200,call(post(staff+"/assign?takeOverFrom=2"),1,"ROLE_PLATFORM_ADMIN",null).status());
        var get = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/support/case");
        var tracked = mvc.perform(get.header("X-Support-Key",key)).andReturn().getResponse();
        assertTrue(tracked.getContentAsString().contains("We can help"));
        assertEquals(404,call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/support/"+id),3,"ROLE_USER",null).status());
        assertEquals(404,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/support/case")
                .header("X-Support-Key","wrong")).andReturn().getResponse().getStatus());
        assertEquals(200,call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(staff+"/state?state=RESOLVED"),1,"ROLE_PLATFORM_ADMIN",null).status());
        assertEquals(409,mvc.perform(post("/api/public/support/case/replies").header("X-Support-Key",key)
                .contentType("application/json").content("{\"message\":\"another reply\"}")).andReturn().getResponse().getStatus());
    }

    record Reply(int status, JsonNode body) {}

    Reply call(MockHttpServletRequestBuilder req, long user, String role, Object body) throws Exception {
        var principal = new UserPrincipal(user, "lifecycle", "lifecycle@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        req.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(req).andReturn().getResponse();
        String text = response.getContentAsString();
        return new Reply(response.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
    Reply owner(String path) throws Exception { return call(post(path), 2, "ROLE_SPA_OWNER", null); }
    String action(long booking, String name) { return "/api/owner/spas/1/bookings/" + booking + "/" + name; }
    String status(long booking) { sync(); return jdbc.queryForObject("select status from booking where id=?", String.class, booking); }
    int bookedUnits(long slot) { sync(); return jdbc.queryForObject("select booked_units from slot where id=?", Integer.class, slot); }
    /** Push pending JPA changes to the database and drop cached entities so direct SQL edits are seen. */
    void sync() { em.flush(); em.clear(); }

    long slot(int hour) throws Exception {
        // Therapist 1 must be rostered for the day before anything can be booked.
        var roster = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(SCHEDULE + "/therapists/1/days/" + day),
                2, "ROLE_SPA_OWNER", Map.of("intervals", List.of(Map.of("kind", "WORK",
                        "start", day.atTime(9, 0).atZone(ZONE).toOffsetDateTime().toString(),
                        "end", day.atTime(18, 0).atZone(ZONE).toOffsetDateTime().toString()))));
        assertEquals(200, roster.status(), () -> roster.body().toString());
        var room = call(post(SCHEDULE + "/rooms"), 2, "ROLE_SPA_OWNER", Map.of("name", "Lifecycle room", "active", true));
        assertEquals(200, room.status(), () -> room.body().toString());
        String start = day.atTime(hour, 0).atZone(ZONE).toOffsetDateTime().toString();
        String end = day.atTime(hour + 1, 0).atZone(ZONE).toOffsetDateTime().toString();
        var created = call(post(SCHEDULE + "/slots"), 2, "ROLE_SPA_OWNER",
                Map.of("roomId", room.body().get("id").asLong(), "serviceId", 1, "start", start, "end", end, "capacity", 1));
        assertEquals(200, created.status(), () -> created.body().toString());
        return created.body().get("id").asLong();
    }
    long book(long slot) throws Exception {
        var reply = call(post("/api/bookings"), 3, "ROLE_USER",
                Map.of("slotId", slot, "spaId", 1, "serviceId", 1, "guests", 1, "paymentMode", "OFFLINE"));
        assertEquals(2, reply.status() / 100, () -> reply.body().toString());
        return reply.body().get("bookingId").asLong();
    }
    /** Move the appointment into the past; the wide margin makes time-zone storage irrelevant. */
    void moveToPast(long booking, int daysAgo) {
        sync();
        jdbc.update("update booking set start_ts = now() - interval ? day, end_ts = now() - interval ? day + interval 1 hour where id=?",
                daysAgo, daysAgo, booking);
    }
    void makeUnpaid(long booking, int createdDaysAgo) {
        sync();
        jdbc.update("update booking set status='PENDING_PAYMENT', payment_mode='ONLINE', created_at = now() - interval ? day where id=?",
                createdDaysAgo, booking);
    }

    @Test void changingCancellationTermsRequiresReviewAndLeavesTheBookingIntact() throws Exception {
        long booking = book(slot(10));
        String url = "/api/bookings/" + booking;
        var preview = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url + "/cancellation-quote"), 3, "ROLE_USER", null);
        assertEquals(200, preview.status());
        assertEquals(0, preview.body().get("refundCents").asInt());
        var stale = preview.body().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)stale).put("refundCents", 100);
        assertTrue(call(post(url + "/cancel"), 3, "ROLE_USER", stale).status() >= 400);
        assertEquals("CONFIRMED", status(booking));
        assertEquals(200, call(post(url + "/cancel"), 3, "ROLE_USER", preview.body()).status());
    }

    @Test void futureAppointmentCannotBeCheckedInCompletedOrMarkedNoShow() throws Exception {
        long booking = book(slot(10));
        for (String name : List.of("arrive", "complete", "no-show"))
            assertEquals(409, owner(action(booking, name)).status(), name);
        assertEquals("CONFIRMED", status(booking));
    }

    @Test void spaChecksInThenCompletesAndTheStatusIsFinal() throws Exception {
        long booking = book(slot(10));
        moveToPast(booking, 3);
        var arrived = owner(action(booking, "arrive"));
        assertEquals(200, arrived.status(), () -> arrived.body().toString());
        assertEquals("ARRIVED", arrived.body().get("status").asText());
        assertEquals(409, owner(action(booking, "no-show")).status(), "an arrived guest is not a no-show");
        assertEquals("COMPLETED", owner(action(booking, "complete")).body().get("status").asText());
        sync();
        assertNotNull(jdbc.queryForObject("select completed_at from booking where id=?", Object.class, booking));
        for (String name : List.of("arrive", "complete", "no-show"))
            assertEquals(409, owner(action(booking, name)).status(), name);
        assertEquals("COMPLETED", status(booking));
    }

    @Test void noShowIsRecordedAndCannotBeCompletedAfterwards() throws Exception {
        long booking = book(slot(11));
        moveToPast(booking, 3);
        assertEquals("NO_SHOW", owner(action(booking, "no-show")).body().get("status").asText());
        assertEquals(409, owner(action(booking, "complete")).status());
        assertEquals("NO_SHOW", status(booking));
    }

    @Test void customersAndOtherSpasCannotChangeABooking() throws Exception {
        long booking = book(slot(12));
        moveToPast(booking, 3);
        assertEquals(403, call(post(action(booking, "complete")), 3, "ROLE_USER", null).status());
        assertEquals(403, call(post(action(booking, "complete")), 999_999, "ROLE_SPA_OWNER", null).status());
        // An administrator passes the spa check, but the booking does not belong to that spa.
        assertEquals(404, call(post("/api/owner/spas/999999/bookings/" + booking + "/complete"), 1, "ROLE_PLATFORM_ADMIN", null).status());
        assertEquals("CONFIRMED", status(booking));
    }

    @Test void unpaidBookingExpiresAndReleasesItsSlot() throws Exception {
        long slot = slot(13);
        long booking = book(slot);
        assertEquals(1, bookedUnits(slot));
        makeUnpaid(booking, 0);
        sync();
        assertFalse(lifecycle.expireUnpaid(booking), "still inside the payment window");
        assertEquals("PENDING_PAYMENT", status(booking));

        makeUnpaid(booking, 3);
        sync();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(bookings.findExpirablePendingPaymentIds(now.minusMinutes(30), now).contains(booking));
        assertTrue(lifecycle.expireUnpaid(booking));
        assertEquals("CANCELLED", status(booking));
        assertEquals("SYSTEM", jdbc.queryForObject("select cancelled_by from booking where id=?", String.class, booking));
        assertEquals("PAYMENT_TIMEOUT", jdbc.queryForObject("select cancellation_reason_code from booking where id=?", String.class, booking));
        assertEquals(0, bookedUnits(slot));
        assertEquals("OPEN", jdbc.queryForObject("select status from slot where id=?", String.class, slot));
        assertFalse(lifecycle.expireUnpaid(booking), "expiry happens once");
        // The freed slot can be booked again.
        book(slot);
    }

    @Test void confirmedBookingIsNeverExpired() throws Exception {
        long slot = slot(14);
        long booking = book(slot);
        sync();
        jdbc.update("update booking set created_at = now() - interval 3 day where id=?", booking);
        sync();
        assertFalse(lifecycle.expireUnpaid(booking));
        assertEquals("CONFIRMED", status(booking));
        assertEquals(1, bookedUnits(slot));
    }

    /** Place the appointment relative to now, and say how long ago it was confirmed. */
    void place(long booking, int startsInHours, int confirmedHoursAgo) {
        sync();
        var b = bookings.findById(booking).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        b.setStartTs(now.plusHours(startsInHours));
        b.setEndTs(now.plusHours(startsInHours + 1));
        b.setLastStatusChangedAt(now.minusHours(confirmedHoursAgo));
        bookings.saveAndFlush(b);
        em.clear();
    }
    int reminderLogs() {
        sync();
        return jdbc.queryForObject("select count(*) from notification_log where template_code='BOOKING_REMINDER' and user_id=3", Integer.class);
    }
    boolean reminderHandled(long booking) {
        sync();
        return jdbc.queryForObject("select reminder_processed_at is not null from booking where id=?", Boolean.class, booking);
    }

    @Test void reminderIsSentOnceForAnAppointmentInsideTheWindow() throws Exception {
        long booking = book(slot(10));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertFalse(lifecycle.sendReminder(booking, 24), "months away: not due");
        assertFalse(reminderHandled(booking));
        assertFalse(bookings.findReminderDueIds(now, now.plusHours(24)).contains(booking));

        place(booking, 6, 72);
        int before = reminderLogs();
        assertTrue(bookings.findReminderDueIds(now, now.plusHours(24)).contains(booking));
        assertTrue(lifecycle.sendReminder(booking, 24));
        assertEquals(before + 1, reminderLogs());
        assertTrue(reminderHandled(booking));
        assertFalse(lifecycle.sendReminder(booking, 24), "never sent twice");
        assertEquals(before + 1, reminderLogs());
        assertFalse(bookings.findReminderDueIds(now, now.plusHours(24)).contains(booking));
    }

    @Test void lastMinuteBookingGetsNoReminderOnTopOfItsConfirmation() throws Exception {
        long booking = book(slot(11));
        place(booking, 6, 0);
        int before = reminderLogs();
        assertFalse(lifecycle.sendReminder(booking, 24));
        assertTrue(reminderHandled(booking), "handled, so the job does not keep revisiting it");
        assertEquals(before, reminderLogs());
    }

    @Test void cancelledBookingGetsNoReminder() throws Exception {
        long booking = book(slot(12));
        place(booking, 6, 72);
        sync();
        jdbc.update("update booking set status='CANCELLED' where id=?", booking);
        int before = reminderLogs();
        assertFalse(lifecycle.sendReminder(booking, 24));
        assertEquals(before, reminderLogs());
    }

    @Test void reschedulingMakesTheNewTimeEligibleForAReminderAgain() throws Exception {
        long first = slot(13), second = slot(15);
        long booking = book(first);
        sync();
        jdbc.update("update booking set reminder_processed_at = now() where id=?", booking);
        var moved = call(post("/api/bookings/" + booking + "/reschedule"), 3, "ROLE_USER", Map.of("targetSlotId", second));
        assertEquals(200, moved.status(), () -> moved.body().toString());
        assertFalse(reminderHandled(booking));
    }

    Reply spaCancel(long booking, long user, String role, Object body) throws Exception {
        return call(post(action(booking, "cancel")), user, role, body);
    }

    @Test void spaCancelsABookingWithAReasonAndTheSlotIsFreed() throws Exception {
        long slot = slot(10);
        long booking = book(slot);
        assertEquals(1, bookedUnits(slot));
        assertEquals(400, spaCancel(booking, 2, "ROLE_SPA_OWNER", Map.of("reason", " ")).status(), "a reason is required");
        assertEquals(403, spaCancel(booking, 3, "ROLE_USER", Map.of("reason", "x")).status());
        assertEquals("CONFIRMED", status(booking));

        var cancelled = spaCancel(booking, 2, "ROLE_SPA_OWNER", Map.of("reason", "Therapist is unwell"));
        assertEquals(200, cancelled.status(), () -> cancelled.body().toString());
        assertEquals("CANCELLED", cancelled.body().get("status").asText());
        assertFalse(cancelled.body().get("refundInitiated").asBoolean(), "nothing was paid online");
        assertEquals("CANCELLED", status(booking));
        assertEquals("SPA", jdbc.queryForObject("select cancelled_by from booking where id=?", String.class, booking));
        assertEquals("Therapist is unwell", jdbc.queryForObject("select cancellation_reason_text from booking where id=?", String.class, booking));
        assertEquals(0, bookedUnits(slot));
        assertEquals(409, spaCancel(booking, 2, "ROLE_SPA_OWNER", Map.of("reason", "again")).status());
        book(slot); // the time can be sold again
    }

    @Test void administratorCancellationIsRecordedAsSuch() throws Exception {
        long booking = book(slot(11));
        assertEquals(200, spaCancel(booking, 1, "ROLE_PLATFORM_ADMIN", Map.of("reason", "Spa closed by the platform")).status());
        assertEquals("ADMIN", jdbc.queryForObject("select cancelled_by from booking where id=?", String.class, booking));
    }

    @Test void spaCannotCancelFinishedOrCheckedInAppointments() throws Exception {
        long ended = book(slot(12));
        moveToPast(ended, 3);
        assertEquals(409, spaCancel(ended, 2, "ROLE_SPA_OWNER", Map.of("reason", "late")).status());
        owner(action(ended, "arrive"));
        assertEquals(409, spaCancel(ended, 2, "ROLE_SPA_OWNER", Map.of("reason", "late")).status());
        assertEquals("ARRIVED", status(ended));
    }

    Reply bookWith(long slot, Map<String, Object> extra) throws Exception {
        sync();
        var body = new java.util.HashMap<String, Object>(Map.of("slotId", slot, "spaId", 1, "serviceId", 1, "guests", 1, "paymentMode", "OFFLINE"));
        body.putAll(extra);
        return call(post("/api/bookings"), 3, "ROLE_USER", body);
    }
    int points() { sync(); return jdbc.queryForObject("select loyalty_points from app_user where id=3", Integer.class); }
    int bookingInt(long booking, String column) { sync(); return jdbc.queryForObject("select " + column + " from booking where id=?", Integer.class, booking); }
    Map<String, Object> couponInput(String code) {
        return new java.util.HashMap<>(Map.of("code", code, "discountType", "PERCENTAGE", "percentOff", 10,
                "validFrom", OffsetDateTime.now().minusDays(1).toString(), "validTo", OffsetDateTime.now().plusDays(30).toString(),
                "maxUsesPerUser", 1));
    }

    @Test void onlyAdministratorsManageCouponsAndCodesAreUnique() throws Exception {
        String admin = "/api/admin/coupons";
        assertEquals(403, call(post(admin), 3, "ROLE_USER", couponInput("LIFE10")).status());
        assertEquals(403, call(post(admin), 2, "ROLE_SPA_OWNER", couponInput("LIFE10")).status());
        var created = call(post(admin), 1, "ROLE_PLATFORM_ADMIN", couponInput("life10"));
        assertEquals(200, created.status(), () -> created.body().toString());
        assertEquals("LIFE10", created.body().get("code").asText());
        assertEquals(10, created.body().get("percentOff").asInt());
        assertEquals(409, call(post(admin), 1, "ROLE_PLATFORM_ADMIN", couponInput("LIFE10")).status());
        var missingValue = couponInput("LIFE20");
        missingValue.remove("percentOff");
        assertEquals(400, call(post(admin), 1, "ROLE_PLATFORM_ADMIN", missingValue).status());

        var retire = couponInput("LIFE10");
        retire.put("active", false);
        String one = admin + "/" + created.body().get("id").asLong();
        var retired = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(one), 1, "ROLE_PLATFORM_ADMIN", retire);
        assertEquals(200, retired.status(), () -> retired.body().toString());
        assertFalse(retired.body().get("active").asBoolean());
        assertEquals(400, bookWith(slot(10), Map.of("couponCode", "LIFE10")).status(), "a retired coupon is refused");
        var listed = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(admin), 1, "ROLE_PLATFORM_ADMIN", null);
        assertTrue(listed.body().toString().contains("LIFE10"));
    }

    @Test void couponIsLimitedPerCustomerAndACancelledBookingGivesTheUseBack() throws Exception {
        assertEquals(200, call(post("/api/admin/coupons"), 1, "ROLE_PLATFORM_ADMIN", couponInput("ONCE10")).status());
        long first = slot(10), second = slot(12);
        assertEquals(400, bookWith(first, Map.of("couponCode", "NOSUCHCODE")).status());

        var booked = bookWith(first, Map.of("couponCode", "once10"));
        assertEquals(2, booked.status() / 100, () -> booked.body().toString());
        long booking = booked.body().get("bookingId").asLong();
        assertTrue(booked.body().get("discountCents").asInt() >= booked.body().get("priceCents").asInt() / 10);
        sync();
        assertEquals("ONCE10", jdbc.queryForObject("select coupon_code from booking where id=?", String.class, booking));
        assertNotNull(jdbc.queryForObject("select coupon_id from booking where id=?", Long.class, booking));

        var again = bookWith(second, Map.of("couponCode", "ONCE10"));
        assertEquals(400, again.status(), () -> again.body().toString());
        assertTrue(again.body().get("message").asText().contains("already used"));

        assertEquals(200, spaCancel(booking, 2, "ROLE_SPA_OWNER", Map.of("reason", "Room unavailable")).status());
        assertEquals(2, bookWith(second, Map.of("couponCode", "ONCE10")).status() / 100, "the cancelled booking no longer counts");
    }

    @Test void loyaltyPointsSpentOnACancelledBookingAreReturned() throws Exception {
        sync();
        // Points live in the ledger as lots; the balance column follows it.
        jdbc.update("delete from loyalty_ledger where user_id=3");
        jdbc.update("insert into loyalty_ledger(user_id,entry_type,points,balance_after,remaining,expires_at,created_at)"
                + " values(3,'ADJUSTMENT',700,700,700,utc_timestamp(6) + interval 6 month,utc_timestamp(6))");
        jdbc.update("update app_user set loyalty_points=700 where id=3");
        var booked = bookWith(slot(10), Map.of("redeemLoyaltyPoints", true));
        assertEquals(2, booked.status() / 100, () -> booked.body().toString());
        long booking = booked.body().get("bookingId").asLong();
        int spent = bookingInt(booking, "loyalty_points_redeemed");
        assertTrue(spent > 0);
        assertEquals(700 - spent, points());

        assertEquals(200, spaCancel(booking, 2, "ROLE_SPA_OWNER", Map.of("reason", "Therapist is unwell")).status());
        assertEquals(700, points());
        assertEquals(0, bookingInt(booking, "loyalty_points_redeemed"));
    }

    @Test void completingAnAppointmentEarnsPointsExactlyOnce() throws Exception {
        sync();
        jdbc.update("update app_user set loyalty_points=0 where id=3");
        long booking = book(slot(11));
        long noShow = book(slot(13));
        moveToPast(booking, 3);
        moveToPast(noShow, 3);
        int paid = bookingInt(booking, "price_cents + tax_cents - discount_cents");
        assertTrue(paid > 0);
        // Points are earned on money received: here, paid in full at the desk.
        jdbc.update("insert into spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method)"
                + " values(?,1,2,utc_timestamp(6),?,'INR','CASH')", booking, paid);
        int earned = paid / 10000; // 1 point per Rs 100
        assertTrue(earned > 0);
        assertEquals(200, owner(action(booking, "complete")).status());
        assertEquals(earned, bookingInt(booking, "loyalty_points_earned"));
        assertEquals(earned, points());
        owner(action(booking, "complete"));
        assertEquals(earned, points(), "a second attempt earns nothing more");
        owner(action(noShow, "no-show"));
        assertEquals(earned, points(), "a no-show earns nothing");
    }

    @Test void pastAppointmentIsCompletedAutomaticallyButRecentOnesAreLeftAlone() throws Exception {
        long old = book(slot(15));
        long upcoming = book(slot(16));
        moveToPast(old, 3);
        sync();
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusHours(24);
        assertFalse(lifecycle.autoComplete(old, cutoff));
        jdbc.update("UPDATE booking SET status='ARRIVED' WHERE id=?", old);
        sync();
        var due = bookings.findAutoCompletableIds(cutoff);
        assertTrue(due.contains(old));
        assertFalse(due.contains(upcoming));
        assertTrue(lifecycle.autoComplete(old, cutoff));
        assertFalse(lifecycle.autoComplete(upcoming, cutoff));
        assertEquals("COMPLETED", status(old));
        assertEquals("CONFIRMED", status(upcoming));
    }
    @Test void completedVisitHasDownloadablePrivateInvoiceAfterCapture() throws Exception {
        long booking = book(slot(12));
        moveToPast(booking, 3);
        assertEquals(200, owner(action(booking, "complete")).status());
        var payment = new com.nirvana.application.model.Payment();
        payment.setBooking(bookings.findById(booking).orElseThrow());
        payment.setPaymentStatus(com.nirvana.application.model.enums.PaymentStatus.INIT);
        payment.setGateway("test");
        payment.setTotalPrice(java.math.BigDecimal.valueOf(1999));
        payment.setAmountCents(199900);
        em.persist(payment);
        em.flush();
        payment.setTransactionId("capture-" + booking);
        payment.setPaymentStatus(com.nirvana.application.model.enums.PaymentStatus.CAPTURED);
        sync();
        String url = "/api/bookings/" + booking + "/invoice/pdf";
        var guest = new UserPrincipal(3L, "guest", "guest@example.test", "", true, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .with(authentication(new UsernamePasswordAuthenticationToken(guest, null, guest.getAuthorities()))))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        try (var document = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("1,999.00"), text);
        }
        assertEquals(403, call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), 2, "ROLE_CUSTOMER", null).status());
    }
}
