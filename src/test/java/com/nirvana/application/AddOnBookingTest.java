package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.gst.GstDocument;
import com.nirvana.application.service.gst.GstInvoiceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Add-ons through the real checkout. Spa 1 sells treatment 1 (60 min, 12,000) with 18% GST and one
 * therapist. A Monday 10:00 slot gets 5% off-peak on the treatment only: with hot stones (50,000)
 * and a 30-minute extension (40,000) the price is 102,000, less 600, plus 18% of 101,400 = 18,252,
 * for 119,652.
 */
@SpringBootTest(properties = "reliability.rate-limiting.enabled=false")
@AutoConfigureMockMvc @Transactional
class AddOnBookingTest {
    static final String SCHEDULE = "/api/owner/spas/1/schedule";
    static final String MENU = "/api/owner/spas/1/add-ons";
    static final ZoneId ZONE = ZoneId.of("Europe/London");
    static final long CUSTOMER = 3, OWNER = 2;
    static final int SERVICE = 12_000, STONES = 50_000, EXTENSION = 40_000, OFF_PEAK = 600, TAX = 18_252, TOTAL = 119_652;
    final LocalDate day = LocalDate.now(ZONE).plusMonths(5).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired GstInvoiceService gst;
    String currency;

    @BeforeEach void setUp() {
        // Only a spa registered for GST may charge it (see GstInvoiceTest).
        jdbc.update("update spa set gstin='09AABCF1234B1ZA', tax_percent=18 where id=1");
        jdbc.update("update service set gst_rate_pct=null where id=1");
        // Therapist 1 alone, whatever other tests left behind: availability depends on who else could take the slot.
        jdbc.update("update therapist set is_active=0 where spa_id=1 and id<>1");
        currency = jdbc.queryForObject("select currency from service where id=1", String.class);
    }

    record Reply(int status, JsonNode body) {}

    Reply call(MockHttpServletRequestBuilder req, long user, String role, Object body) throws Exception {
        if (role != null) {
            var principal = new UserPrincipal(user, "addons", "addons@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
            req.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        }
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(req).andReturn().getResponse();
        String text = response.getContentAsString();
        return new Reply(response.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
    Reply owner(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, OWNER, "ROLE_SPA_OWNER", body); }
    Reply guest(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, CUSTOMER, "ROLE_USER", body); }
    void sync() { em.flush(); em.clear(); }

    Map<String, Object> addOn(String name, int price, int minutes, boolean active, List<Long> services) {
        var input = new HashMap<String, Object>(Map.of("name", name, "description", name + " for the treatment", "priceCents", price,
                "currency", currency, "extraMinutes", minutes, "active", active, "serviceIds", services));
        input.put("sortOrder", minutes);
        return input;
    }
    long create(String name, int price, int minutes, boolean active, List<Long> services) throws Exception {
        var made = owner(post(MENU), addOn(name, price, minutes, active, services));
        assertEquals(200, made.status(), () -> made.body().toString());
        return made.body().get("id").asLong();
    }

    String at(int hour, int minute) { return day.atTime(hour, minute).atZone(ZONE).toOffsetDateTime().toString(); }

    /** A one-hour slot for treatment 1 in a room of its own; therapist 1 works 9:00 to 18:00. */
    long slot(int hour) throws Exception {
        var roster = owner(put(SCHEDULE + "/therapists/1/days/" + day), Map.of("intervals", List.of(Map.of("kind", "WORK",
                "start", at(9, 0), "end", at(18, 0)))));
        assertEquals(200, roster.status(), () -> roster.body().toString());
        var room = owner(post(SCHEDULE + "/rooms"), Map.of("name", "Add-on room " + hour, "active", true));
        assertEquals(200, room.status(), () -> room.body().toString());
        var created = owner(post(SCHEDULE + "/slots"), Map.of("roomId", room.body().get("id").asLong(), "serviceId", 1,
                "start", at(hour, 0), "end", at(hour + 1, 0), "capacity", 1));
        assertEquals(200, created.status(), () -> created.body().toString());
        return created.body().get("id").asLong();
    }
    Reply book(long slot, List<Long> addOns, Map<String, Object> extra) throws Exception {
        sync();
        var body = new HashMap<String, Object>(Map.of("slotId", slot, "spaId", 1, "serviceId", 1, "guests", 1, "paymentMode", "OFFLINE",
                "addOnIds", addOns));
        body.putAll(extra);
        return guest(post("/api/bookings"), body);
    }
    long booked(Reply reply) {
        assertEquals(2, reply.status() / 100, () -> reply.body().toString());
        return reply.body().get("bookingId").asLong();
    }
    Set<Long> listed(List<Long> addOns) throws Exception {
        var req = get("/api/spas/1/services/1/slots").param("date", day.toString());
        for (Long id : addOns) req.param("addOnIds", id.toString());
        var reply = guest(req, null);
        assertEquals(200, reply.status(), () -> reply.body().toString());
        Set<Long> ids = new HashSet<>();
        reply.body().forEach(s -> ids.add(s.get("slotId").asLong()));
        return ids;
    }
    long minutes(long booking) {
        sync();
        var row = jdbc.queryForMap("select start_ts,end_ts from booking where id=?", booking);
        return Duration.between(((LocalDateTime) row.get("start_ts")), ((LocalDateTime) row.get("end_ts"))).toMinutes();
    }

    Map<String, Object> holdBody(long slot, long addOn) {
        return Map.of("spaId", 1, "serviceId", 1, "slotId", slot, "guests", 1, "addOnIds", List.of(addOn));
    }

    // ------------------------------------------------------------------ the owner's menu

    @Test void ownersManageTheMenuAndGuestsSeeOnlyWhatAppliesToTheirTreatment() throws Exception {
        assertEquals(403, guest(post(MENU), addOn("Hot stones", STONES, 0, true, List.of())).status(), "a guest can't add to the menu");
        long stones = create("Hot stones", STONES, 0, true, List.of(1L));
        create("Aromatherapy oil", 15_000, 0, true, List.of());
        create("Retired scrub", 9_000, 0, false, List.of());
        create("Facial mask", 20_000, 0, true, List.of(2L));
        assertEquals(400, owner(post(MENU), addOn("Elsewhere", 1_000, 0, true, List.of(999_999L))).status(),
                "treatments must be this spa's");

        // Public, like the treatments themselves.
        var menu = call(get("/api/spas/1/add-ons").param("serviceId", "1"), 0, null, null);
        assertEquals(200, menu.status(), () -> menu.body().toString());
        List<String> names = new ArrayList<>();
        menu.body().forEach(a -> names.add(a.get("name").asText()));
        assertEquals(List.of("Aromatherapy oil", "Hot stones"), names.stream().sorted().toList());
        assertFalse(menu.body().get(0).has("version"), "guests don't see the owner's bookkeeping");

        assertEquals(4, owner(get(MENU), null).body().size(), "the owner sees inactive add-ons too");
        var edit = addOn("Hot stones", 55_000, 0, true, List.of(1L));
        edit.put("version", 99);
        assertEquals(409, owner(put(MENU + "/" + stones), edit).status(), "a stale edit is refused");
        edit.put("version", 0);
        var saved = owner(put(MENU + "/" + stones), edit);
        assertEquals(200, saved.status(), () -> saved.body().toString());
        assertEquals(55_000, saved.body().get("priceCents").asInt());
        assertEquals(1, saved.body().get("version").asInt());

        assertEquals(403, guest(delete(MENU + "/" + stones), null).status());
        assertEquals(200, owner(delete(MENU + "/" + stones), null).status());
        assertEquals(3, owner(get(MENU), null).body().size());
        assertEquals(404, owner(delete(MENU + "/" + stones), null).status());
    }

    // ------------------------------------------------------------------ price, tax, desk and invoice

    @Test void frontDeskBooksTheExtendedVisitAtTheMenuPrice() throws Exception {
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long slot = slot(10);
        var request = Map.of("slotId", slot, "name", "Test guest", "channel", "WALK_IN",
                "requestKey", "desk-addon-test", "addOnIds", List.of(extension));
        var reply = owner(post("/api/owner/spas/1/desk-appointments"), request);
        assertEquals(200, reply.status(), () -> reply.body().toString());
        long id = reply.body().asLong();
        assertEquals(SERVICE + EXTENSION, jdbc.queryForObject("select price_cents from booking where id=?", Integer.class, id));
        assertEquals(90, jdbc.queryForObject("select timestampdiff(minute,start_ts,end_ts) from booking where id=?", Integer.class, id));
        assertEquals(id, owner(post("/api/owner/spas/1/desk-appointments"), request).body().asLong());
    }

    @Test void theServerPricesAddOnsAndTheyFlowThroughTaxTheDeskAndTheInvoice() throws Exception {
        long stones = create("Hot stones", STONES, 0, true, List.of(1L));
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long slot = slot(10);

        var preview = guest(post("/api/v1/checkout/preview"), Map.of("spaId", 1, "serviceId", 1, "slotId", slot, "guests", 1,
                "addOnIds", List.of(stones, extension)));
        assertEquals(200, preview.status(), () -> preview.body().toString());
        assertEquals(SERVICE, preview.body().get("serviceCents").asInt());
        assertEquals(SERVICE + STONES + EXTENSION, preview.body().get("priceCents").asInt());
        assertEquals(OFF_PEAK, preview.body().get("discountCents").asInt(), "off-peak is on the treatment only");
        assertEquals(TAX, preview.body().get("taxCents").asInt());
        assertEquals(TOTAL, preview.body().get("totalCents").asInt());
        assertEquals(2, preview.body().get("addOns").size());
        assertEquals(30, preview.body().get("extraMinutes").asInt());

        // A price sent by the client is ignored: the server prices from the menu.
        var reply = book(slot, List.of(stones, extension), Map.of("priceCents", 1, "totalCents", 1));
        long booking = booked(reply);
        assertEquals(SERVICE + STONES + EXTENSION, reply.body().get("priceCents").asInt());
        assertEquals(SERVICE, reply.body().get("serviceCents").asInt());
        assertEquals(TAX, reply.body().get("taxCents").asInt());
        assertEquals(TOTAL, reply.body().get("totalCents").asInt());
        assertEquals("Hot stones", reply.body().get("addOns").get(0).get("name").asText());
        assertEquals(90, minutes(booking), "the extension lengthens the appointment");
        assertEquals(TOTAL, jdbc.queryForObject("select remainder_cents from booking where id=?", Integer.class, booking));

        // Later edits don't change what was sold.
        var edit = addOn("Hot stones", 99_900, 0, true, List.of(1L));
        edit.put("version", 0);
        assertEquals(200, owner(put(MENU + "/" + stones), edit).status());
        sync();
        assertEquals(List.of(STONES, EXTENSION), jdbc.queryForList(
                "select amount_cents from booking_addon where booking_id=? order by amount_cents desc", Integer.class, booking));
        var mine = guest(get("/api/me/bookings"), null);
        assertEquals(200, mine.status(), () -> mine.body().toString());
        JsonNode listed = null;
        for (var b : mine.body().get("page").get("content")) if (b.get("bookingId").asLong() == booking) listed = b;
        assertNotNull(listed);
        assertEquals(STONES, listed.get("addOns").get(0).get("amountCents").asInt());
        assertEquals(TOTAL, listed.get("payAtSpaCheckoutCents").asInt());

        // The front desk sees each add-on and collects the full balance.
        var desk = owner(get("/api/owner/spas/1/desk/records").param("period", "CUSTOM")
                .param("from", day.toString()).param("to", day.toString()), null);
        assertEquals(200, desk.status(), () -> desk.body().toString());
        JsonNode row = null;
        for (var r : desk.body().get("items")) if (r.get("id").asLong() == booking) row = r;
        assertNotNull(row, () -> desk.body().toString());
        assertEquals(TOTAL, row.get("payable").asInt());
        assertEquals(2, row.get("addOns").size());
        assertEquals("Extension", row.get("addOns").get(1).get("name").asText());
        var collected = owner(post("/api/owner/spas/1/desk/bookings/" + booking + "/collect"), Map.of("method", "CASH"));
        assertEquals(2, collected.status() / 100, () -> collected.body().toString());
        sync();
        assertEquals(TOTAL, jdbc.queryForObject("select sum(amount_cents) from spa_desk_receipt where booking_id=?", Integer.class, booking));

        // The GST invoice is for the whole supply and lists each add-on.
        GstDocument invoice = gst.issueForBooking(booking).orElseThrow();
        assertEquals(GstDocument.TAX_INVOICE, invoice.docType());
        assertEquals(SERVICE + STONES + EXTENSION, invoice.grossCents());
        assertEquals(OFF_PEAK, invoice.discountCents());
        assertEquals(TAX, invoice.taxCents());
        assertEquals(TOTAL, invoice.totalCents());
        assertTrue(invoice.description().contains("with Hot stones, Extension"), invoice.description());
        var html = mvc.perform(get("/api/gst-documents/" + invoice.id() + "/html").with(authentication(ownerAuth())))
                .andReturn().getResponse();
        assertEquals(200, html.getStatus());
        String page = html.getContentAsString();
        assertTrue(page.contains("Charges"), "a line per charge");
        assertTrue(page.contains("Hot stones") && page.contains("Extension"));
    }

    UsernamePasswordAuthenticationToken ownerAuth() {
        var principal = new UserPrincipal(OWNER, "addons", "addons@example.test", "", true, List.of(new SimpleGrantedAuthority("ROLE_SPA_OWNER")));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test void onlyThisSpasActiveAddOnsForTheTreatmentCanBeBooked() throws Exception {
        long retired = create("Retired scrub", 9_000, 0, false, List.of());
        long facial = create("Facial mask", 20_000, 0, true, List.of(2L));
        long other = GstRows.copy(jdbc, "spa", 1, Map.of());
        jdbc.update("insert into spa_addon(spa_id,name,price_cents,currency,extra_minutes) values(?,?,?,?,0)", other, "Elsewhere", 100, currency);
        long elsewhere = jdbc.queryForObject("select max(id) from spa_addon where spa_id=?", Long.class, other);
        long slot = slot(14);
        for (long id : List.of(retired, facial, elsewhere, 987_654L)) {
            var refused = book(slot, List.of(id), Map.of());
            assertEquals(400, refused.status(), () -> "add-on " + id + ": " + refused.body());
        }
        sync();
        assertEquals(0, jdbc.queryForObject("select booked_units from slot where id=?", Integer.class, slot), "nothing was booked");
        assertEquals(400, guest(post("/api/v1/checkout/preview"), Map.of("spaId", 1, "serviceId", 1, "guests", 1,
                "addOnIds", List.of(facial))).status());
    }

    // ------------------------------------------------------------------ availability

    @Test void extraMinutesNeedTheTherapistAndRoomFreeForTheWholeAppointment() throws Exception {
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long late = slot(17); // 17:00-18:00, the therapist leaves at 18:00
        assertTrue(listed(List.of()).contains(late));
        assertFalse(listed(List.of(extension)).contains(late), "17:00 plus 90 minutes runs past the therapist's day");
        assertEquals(409, book(late, List.of(extension), Map.of()).status());
        var hold = guest(post("/api/spas/1/services/1/slots/" + late + "/hold"), holdBody(late, extension));
        assertEquals(409, hold.status(), () -> hold.body().toString());

        long noon = slot(12);
        long next = slot(13); // another room, but the same and only therapist
        assertTrue(listed(List.of()).contains(next));
        long booking = booked(book(noon, List.of(extension), Map.of()));
        assertEquals(90, minutes(booking));
        assertFalse(listed(List.of()).contains(next), "the 12:00 appointment runs until 13:30");
        assertEquals(409, book(next, List.of(), Map.of()).status());

        // Moving the appointment keeps its extra minutes.
        long afternoon = slot(15);
        var moved = guest(post("/api/bookings/" + booking + "/reschedule"), Map.of("targetSlotId", afternoon));
        assertEquals(200, moved.status(), () -> moved.body().toString());
        assertEquals(90, minutes(booking));
        assertTrue(listed(List.of()).contains(next), "13:00 is free again");
    }

    @Test void aReservationKeepsItsAddOnMinutes() throws Exception {
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long noon = slot(12);
        long next = slot(13);
        var hold = guest(post("/api/spas/1/services/1/slots/" + noon + "/hold"), holdBody(noon, extension));
        assertEquals(200, hold.status(), () -> hold.body().toString());
        assertFalse(listed(List.of()).contains(next), "the held 12:00 appointment runs until 13:30");
        long booking = booked(book(noon, List.of(extension), Map.of("holdToken", hold.body().get("holdToken").asText())));
        assertEquals(90, minutes(booking));
    }

    // ------------------------------------------------------------------ refunds

    @Test void aCancellationRefundsTheAddOnsWithTheTreatment() throws Exception {
        long stones = create("Hot stones", STONES, 0, true, List.of(1L));
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long booking = booked(book(slot(10), List.of(stones, extension), Map.of()));
        sync();
        // As if paid online in full, with a 50% fee refunded to the wallet.
        jdbc.update("update booking set payment_mode='ONLINE', remainder_cents=?, policy_snapshot=? where id=?", TOTAL,
                "{\"defaultCutoffMinutes\":15,\"defaultFeePercent\":50,\"defaultRoute\":\"WALLET_CREDIT\"}", booking);
        long template = jdbc.queryForObject("select min(id) from payment", Long.class);
        var payment = new HashMap<String, Object>(Map.of("booking_id", booking, "payment_status", "CAPTURED", "amount_cents", TOTAL,
                "refunded_cents", 0, "captured_at", LocalDateTime.now(ZoneOffset.UTC), "currency", currency, "user_id", CUSTOMER));
        payment.put("refunded_at", null);
        GstRows.copy(jdbc, "payment", template, payment);
        int before = walletBalance();

        var quote = guest(get("/api/bookings/" + booking + "/cancellation-quote"), null);
        assertEquals(200, quote.status(), () -> quote.body().toString());
        assertEquals(TOTAL / 2, quote.body().get("refundCents").asInt(), "half of everything paid, add-ons included");
        assertEquals(TOTAL - TOTAL / 2, quote.body().get("feeCents").asInt());
        var cancelled = guest(post("/api/bookings/" + booking + "/cancel"), quote.body());
        assertEquals(200, cancelled.status(), () -> cancelled.body().toString());
        assertEquals(TOTAL / 2, cancelled.body().get("refundAmountCents").asInt());
        sync();
        assertEquals(before + TOTAL / 2, walletBalance());
        assertEquals(2, jdbc.queryForObject("select count(*) from booking_addon where booking_id=?", Integer.class, booking),
                "the record of what was sold stays");
    }

    int walletBalance() {
        return jdbc.queryForObject("select coalesce(sum(balance_cents),0) from booking_wallet_balance where user_id=? and currency=?",
                Integer.class, CUSTOMER, currency);
    }

    @Test void aPackageSessionCannotPayForAddOns() throws Exception {
        long stones = create("Hot stones", STONES, 0, true, List.of(1L));
        long slot = slot(14);
        var refused = book(slot, List.of(stones), Map.of("payWithSpaPackage", true));
        assertEquals(400, refused.status(), () -> refused.body().toString());
        assertTrue(refused.body().get("message").asText().contains("package"), () -> refused.body().toString());
        // Moving a booking that has add-ons lists times with room for its minutes.
        long late = slot(17);
        var moving = guest(get("/api/spas/1/services/1/slots").param("date", day.toString()).param("extraMinutes", "30"), null);
        assertEquals(200, moving.status());
        Set<Long> ids = new HashSet<>();
        moving.body().forEach(s -> ids.add(s.get("slotId").asLong()));
        assertTrue(ids.contains(slot));
        assertFalse(ids.contains(late));
    }

    @Test void addOnsArePricedPerGuestAndOnlyTimeIsAddedOnce() throws Exception {
        jdbc.update("update service set max_persons=2 where id=1");
        long stones = create("Hot stones", STONES, 0, true, List.of(1L));
        long extension = create("Extension", EXTENSION, 30, true, List.of());
        long slot = slot(14);
        jdbc.update("update slot set capacity_unit=2 where id=?", slot);
        var reply = book(slot, List.of(extension, stones), Map.of("guests", 2));
        long booking = booked(reply);
        assertEquals(2 * (SERVICE + STONES + EXTENSION), reply.body().get("priceCents").asInt());
        assertEquals(2, reply.body().get("addOns").get(0).get("quantity").asInt());
        assertEquals(2 * STONES, reply.body().get("addOns").get(0).get("amountCents").asInt(), "menu order, not request order");
        assertEquals(90, minutes(booking));
    }
}
