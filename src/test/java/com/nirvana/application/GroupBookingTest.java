package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.config.RateLimitingProperties;
import com.nirvana.application.security.RateLimitService;
import com.nirvana.application.security.SharedRateWindow;
import com.nirvana.application.security.UserPrincipal;
import com.razorpay.OrderClient;
import com.razorpay.PaymentClient;
import com.razorpay.RazorpayClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Couples and group bookings against the seeded spa 1 (services 1 and 2, owner 2, customer 3). */
@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "payments.razorpay.enabled=true", "payments.razorpay.key-id=fixture",
        "payments.razorpay.key-secret=fixture-key", "payments.razorpay.webhook-secret=fixture-webhook"
})
@AutoConfigureMockMvc
class GroupBookingTest {
    private static final InheritableThreadLocal<String> rateScope = new InheritableThreadLocal<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class RateFixture {
        @Bean @Primary
        RateLimitService groupBookingRateLimits(RateLimitingProperties properties, StringRedisTemplate redis,
                                                MeterRegistry metrics, SharedRateWindow shared) {
            return new RateLimitService(properties, redis, metrics, shared) {
                @Override public boolean isAllowedSensitive(String key, int limit) { return super.isAllowedSensitive(scoped(key), limit); }
                @Override public boolean isAllowed(String key, int limit) { return super.isAllowed(scoped(key), limit); }
                private String scoped(String key) {
                    String prefix = rateScope.get();
                    return prefix == null || key.startsWith(prefix) ? key : prefix + key;
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean RazorpayClient razorpay;

    static final String BASE = "/api/owner/spas/1/schedule";
    static final ZoneId ZONE = ZoneId.of("Europe/London");
    LocalDate day = LocalDate.now(ZONE).plusMonths(3).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach void scope() { rateScope.set("group-booking-test:" + UUID.randomUUID() + ":"); }
    @AfterEach void release() { rateScope.remove(); }

    record Reply(int status, JsonNode body) {}

    Reply call(MockHttpServletRequestBuilder req, long user, String role, Object body) throws Exception {
        var principal = new UserPrincipal(user, "group", "group@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        req.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(req).andReturn().getResponse();
        String text = response.getContentAsString();
        return new Reply(response.getStatus(), text.isBlank() ? null : json.readTree(text));
    }

    JsonNode ok(Reply r) { assertEquals(200, r.status(), () -> String.valueOf(r.body())); return r.body(); }
    Reply owner(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, 2, "ROLE_SPA_OWNER", body); }
    Reply customer(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, 3, "ROLE_USER", body); }

    OffsetDateTime at(LocalDate d, int hour) { return d.atTime(hour, 0).atZone(ZONE).toOffsetDateTime(); }

    long room() throws Exception {
        return ok(owner(post(BASE + "/rooms"), Map.of("name", "Group room", "active", true))).get("id").asLong();
    }

    long slot(long room, long service, OffsetDateTime start) throws Exception {
        return ok(owner(post(BASE + "/slots"), Map.of("roomId", room, "serviceId", service, "start", start,
                "end", start.plusHours(1), "capacity", 1))).get("id").asLong();
    }

    long therapist(String name) throws Exception {
        return ok(owner(post("/api/owner/spas/1/therapists"), Map.of("name", name, "publicName", name, "available", true,
                "active", true, "serviceIds", List.of(1, 2)))).get("id").asLong();
    }

    /** Only therapists made by this test are free, so counts are exact. Rolled back with the test. */
    void onlyOwnTherapists() {
        jdbc.update("update therapist set is_available=0 where spa_id=1");
    }

    static Map<String, Object> guest(String name, long service) {
        Map<String, Object> g = new HashMap<>();
        g.put("name", name);
        g.put("serviceId", service);
        return g;
    }

    List<String> times(LocalDate d, List<Map<String, Object>> guests) throws Exception {
        var body = ok(customer(post("/api/spas/1/group-availability"), Map.of("date", d.toString(), "guests", guests)));
        List<String> result = new ArrayList<>();
        body.forEach(t -> result.add(OffsetDateTime.parse(t.get("startTs").asText()).toInstant().toString()));
        return result;
    }

    boolean offers(LocalDate d, int hour, List<Map<String, Object>> guests) throws Exception {
        return times(d, guests).contains(at(d, hour).toInstant().toString());
    }

    Map<String, Object> order(OffsetDateTime start, String mode, List<Map<String, Object>> guests) {
        Map<String, Object> body = new HashMap<>();
        body.put("spaId", 1);
        body.put("startTs", start);
        body.put("paymentMode", mode);
        body.put("guests", guests);
        return body;
    }

    @Test @Transactional void aTimeIsOfferedOnlyWhenEveryGuestHasATherapistAndARoom() throws Exception {
        onlyOwnTherapists();
        var start = at(day, 11);
        slot(room(), 1, start);
        slot(room(), 1, start);
        therapist("Asha");
        var pair = List.of(guest("Me", 1), guest("Ravi", 1));
        assertFalse(offers(day, 11, pair), "two rooms but one therapist: no time for two");
        assertFalse(ok(customer(get("/api/spas/1/services/1/slots").param("date", day.toString()), null)).isEmpty(),
                "a single guest can still book it");
        therapist("Bela");
        assertTrue(offers(day, 11, pair), "two qualified therapists and two rooms fit a couple");
        assertFalse(offers(day, 11, List.of(guest("Me", 1), guest("Ravi", 1), guest("Kiran", 1))), "a third guest has no room");

        // A mixed group: one massage and one facial, each with its own room and therapist.
        var later = at(day, 15);
        slot(room(), 1, later);
        slot(room(), 2, later);
        assertTrue(offers(day, 15, List.of(guest("Me", 1), guest("Ravi", 2))));
        assertFalse(offers(day, 15, List.of(guest("Me", 2), guest("Ravi", 2))), "only one facial room at 15:00");
    }

    @Test @Transactional void aCouplesRoomSeatsTwoSideBySideButNoMore() throws Exception {
        onlyOwnTherapists();
        var start = at(day, 12);
        long couplesSlot = slot(room(), 1, start);
        therapist("Asha"); therapist("Bela"); therapist("Chitra");
        var pair = List.of(guest("Me", 1), guest("Ravi", 1));
        assertFalse(offers(day, 12, pair), "one ordinary room seats one guest");
        assertEquals(403, customer(put("/api/owner/spas/1/services/1/side-by-side"), Map.of("capacity", 2)).status());
        assertEquals(2, ok(owner(put("/api/owner/spas/1/services/1/side-by-side"), Map.of("capacity", 2))).get("capacity").asInt());
        assertTrue(offers(day, 12, pair), "a couples room seats both guests");
        assertFalse(offers(day, 12, List.of(guest("Me", 1), guest("Ravi", 1), guest("Kiran", 1))), "capacity 2 takes no third guest");

        var group = ok(customer(post("/api/booking-groups"), order(start, "OFFLINE", pair)));
        assertEquals(2, group.get("members").size());
        long first = group.get("members").get(0).get("bookingId").asLong(), second = group.get("members").get(1).get("bookingId").asLong();
        em.flush();
        assertEquals(couplesSlot, jdbc.queryForObject("select slot_id from booking where id=?", Long.class, first));
        assertEquals(couplesSlot, jdbc.queryForObject("select slot_id from booking where id=?", Long.class, second));
        assertNotEquals(jdbc.queryForObject("select therapist_id from booking where id=?", Long.class, first),
                jdbc.queryForObject("select therapist_id from booking where id=?", Long.class, second));
        assertEquals("CONFIRMED", group.get("members").get(1).get("status").asText());
        // The room is now in use: nobody else can take that time.
        var single = customer(post("/api/bookings"), Map.of("slotId", couplesSlot, "spaId", 1, "serviceId", 1, "guests", 1, "paymentMode", "OFFLINE"));
        assertEquals(409, single.status(), () -> String.valueOf(single.body()));

        // The desk sees the guests linked; checking in one guest leaves the other as booked.
        var desk = ok(owner(get("/api/owner/spas/1/booking-groups").param("from", start.minusHours(1).toString()).param("to", start.plusHours(1).toString()), null));
        var linked = new ArrayList<Long>();
        desk.forEach(g -> g.get("members").forEach(m -> linked.add(m.get("bookingId").asLong())));
        assertTrue(linked.containsAll(List.of(first, second)));
        jdbc.update("update booking set start_ts=? where id in (?,?)", java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)), first, second);
        em.clear();
        var arrived = owner(post("/api/owner/spas/1/bookings/" + first + "/arrive"), null);
        assertEquals(2, arrived.status() / 100, () -> String.valueOf(arrived.body()));
        em.flush();
        assertEquals("ARRIVED", jdbc.queryForObject("select status from booking where id=?", String.class, first));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from booking where id=?", String.class, second));
    }

    @Test @Transactional void holdsAreAllOrNothingAndConvertTogether() throws Exception {
        onlyOwnTherapists();
        var start = at(day, 16);
        slot(room(), 1, start);
        slot(room(), 1, start);
        therapist("Asha"); therapist("Bela");
        int before = jdbc.queryForObject("select count(*) from booking_hold where user_id=3", Integer.class);
        var tooMany = customer(post("/api/booking-groups/holds"), Map.of("spaId", 1, "startTs", start,
                "guests", List.of(guest("Me", 1), guest("Ravi", 1), guest("Kiran", 1))));
        assertEquals(409, tooMany.status(), () -> String.valueOf(tooMany.body()));
        em.flush();
        assertEquals(before, jdbc.queryForObject("select count(*) from booking_hold where user_id=3", Integer.class), "no partial hold is left");

        var pair = List.of(guest("Me", 1), guest("Ravi", 1));
        var hold = ok(customer(post("/api/booking-groups/holds"), Map.of("spaId", 1, "startTs", start, "guests", pair)));
        String token = hold.get("holdToken").asText();
        assertEquals(2, jdbc.queryForObject("select count(*) from booking_group_hold where group_token=?", Integer.class, token));
        assertFalse(offers(day, 16, pair), "the held rooms are no longer offered to another group");

        var body = order(start, "OFFLINE", pair);
        body.put("holdToken", token);
        var group = ok(customer(post("/api/booking-groups"), body));
        assertEquals(2, group.get("members").size());
        em.flush();
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from booking_group_hold g join booking_hold h on h.id=g.hold_id
                where g.group_token=? and h.converted_to_booking=1""", Integer.class, token));
        var reused = customer(post("/api/booking-groups"), body);
        assertEquals(409, reused.status(), () -> String.valueOf(reused.body()));
    }

    /** Commits: two groups race for the same two rooms. One gets both, the other gets nothing. */
    @Test void racingGroupsNeverLeaveAPartialHold() throws Exception {
        LocalDate raceDay = day.plusWeeks(5);
        var start = at(raceDay, 10);
        long[] rooms = {room(), room()};
        long[] slots = {slot(rooms[0], 1, start), slot(rooms[1], 1, start)};
        long[] staff = {therapist("Race A"), therapist("Race B"), therapist("Race C"), therapist("Race D")};
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Reply>> replies = new ArrayList<>();
            for (int i = 0; i < 2; i++) replies.add(pool.submit(() -> {
                gate.await();
                return customer(post("/api/booking-groups/holds"), Map.of("spaId", 1, "startTs", start,
                        "guests", List.of(guest("Me", 1), guest("Friend", 1))));
            }));
            gate.countDown();
            int wins = 0;
            List<String> tokens = new ArrayList<>();
            for (var f : replies) {
                var r = f.get(30, TimeUnit.SECONDS);
                if (r.status() == 200) { wins++; tokens.add(r.body().get("holdToken").asText()); }
                else assertEquals(409, r.status(), () -> String.valueOf(r.body()));
            }
            assertEquals(1, wins);
            assertEquals(2, jdbc.queryForObject("select count(*) from booking_hold where slot_id in (?,?)", Integer.class, slots[0], slots[1]));
            assertEquals(2, jdbc.queryForObject("select count(*) from booking_group_hold where group_token=?", Integer.class, tokens.get(0)));
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from booking_hold where slot_id in (?,?)", slots[0], slots[1]);
            jdbc.update("delete from slot where id in (?,?)", slots[0], slots[1]);
            for (long t : staff) jdbc.update("update therapist set is_available=0, is_active=0 where id=?", t);
        }
    }

    static String sign(String order, String payment) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("fixture-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((order + "|" + payment).getBytes(StandardCharsets.UTF_8)));
    }

    @Test @Transactional void oneOnlinePaymentIsSplitPerGuestAndOneGuestCanCancelWithARefund() throws Exception {
        onlyOwnTherapists();
        jdbc.update("update service set currency='INR' where spa_id=1");
        var start = at(day.plusWeeks(1), 11);
        slot(room(), 1, start);
        slot(room(), 2, start);
        therapist("Asha"); therapist("Bela");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String orderId = "order_" + suffix, paymentId = "pay_" + suffix;
        razorpay.orders = mock(OrderClient.class);
        when(razorpay.orders.create(any())).thenReturn(new com.razorpay.Order(new JSONObject().put("id", orderId)));

        var group = ok(customer(post("/api/booking-groups"), order(start, "ONLINE", List.of(guest("Me", 1), guest("Ravi", 2)))));
        assertEquals(orderId, group.get("razorpay").get("razorpayOrderId").asText());
        int total = group.get("totalCents").asInt();
        assertEquals(total, group.get("razorpay").get("amount").decimalValue().movePointRight(2).intValueExact(), "one order for the whole group");
        long groupId = group.get("groupId").asLong();
        long lead = group.get("members").get(0).get("bookingId").asLong(), guest = group.get("members").get(1).get("bookingId").asLong();
        int leadShare = group.get("members").get(0).get("payableCents").asInt(), guestShare = group.get("members").get(1).get("payableCents").asInt();
        assertEquals(total, leadShare + guestShare);
        assertEquals("PENDING_PAYMENT", group.get("members").get(1).get("status").asText());

        var captured = new JSONObject().put("id", paymentId).put("order_id", orderId).put("amount", total).put("currency", "INR")
                .put("captured", true).put("status", "captured").put("amount_refunded", 0).put("method", "upi");
        razorpay.payments = mock(PaymentClient.class);
        when(razorpay.payments.fetch(paymentId)).thenReturn(new com.razorpay.Payment(captured));
        var confirm = Map.of("razorpay_order_id", orderId, "razorpay_payment_id", paymentId, "razorpay_signature", sign(orderId, paymentId));
        var paid = customer(post("/api/bookings/" + lead + "/payments/razorpay/confirm"), confirm);
        assertEquals(2, paid.status() / 100, () -> String.valueOf(paid.body()));
        assertEquals(2, customer(post("/api/bookings/" + lead + "/payments/razorpay/confirm"), confirm).status() / 100, "confirming twice is harmless");
        em.flush();
        assertEquals(List.of(leadShare, guestShare), jdbc.queryForList("""
                select p.amount_cents from payment p join booking_group_member m on m.booking_id=p.booking_id
                where m.group_id=? order by m.position""", Integer.class, groupId));
        assertEquals(2, jdbc.queryForObject("select count(*) from payment where shared_gateway_payment_id=? and transaction_id is null and payment_status='CAPTURED'",
                Integer.class, paymentId));
        assertEquals("CAPTURED", jdbc.queryForObject("select payment_status from booking_group where id=?", String.class, groupId));
        for (long b : List.of(lead, guest)) assertEquals("CONFIRMED", jdbc.queryForObject("select status from booking where id=?", String.class, b));

        // One guest cancels: only their share is refunded, against the shared provider payment.
        em.clear();
        var cancelled = customer(post("/api/bookings/" + guest + "/cancel"), null);
        assertEquals(2, cancelled.status() / 100, () -> String.valueOf(cancelled.body()));
        em.flush();
        assertEquals("CANCELLED", jdbc.queryForObject("select status from booking where id=?", String.class, guest));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from booking where id=?", String.class, lead));
        var refund = jdbc.queryForMap("select r.amount_cents, p.booking_id, p.shared_gateway_payment_id from payment_refund r join payment p on p.id=r.payment_id where r.booking_id=?", guest);
        assertEquals(guestShare, ((Number) refund.get("amount_cents")).intValue(), "no fee well before the appointment");
        assertEquals(guest, ((Number) refund.get("booking_id")).longValue());
        assertEquals(paymentId, refund.get("shared_gateway_payment_id"));
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_refund where booking_id=?", Integer.class, lead));

        // Then the rest of the group: the quote covers only the guest still booked.
        em.clear();
        var quote = ok(customer(get("/api/booking-groups/" + groupId + "/cancellation-quote"), null));
        assertEquals(1, quote.get("members").size());
        assertEquals(leadShare, quote.get("refundCents").asInt());
        assertEquals(409, customer(post("/api/booking-groups/" + groupId + "/cancel"), Map.of("expectedRefundCents", leadShare + 1)).status());
        ok(customer(post("/api/booking-groups/" + groupId + "/cancel"), Map.of("expectedRefundCents", leadShare)));
        em.flush();
        assertEquals("CANCELLED", jdbc.queryForObject("select status from booking where id=?", String.class, lead));
        assertEquals(leadShare, jdbc.queryForObject("select amount_cents from payment_refund where booking_id=?", Integer.class, lead));
        assertEquals(403, call(get("/api/booking-groups/" + groupId), 2, "ROLE_USER", null).status());
    }

    @Test @Transactional void aPayAtSpaGroupCancelsTogetherAndFreesItsRooms() throws Exception {
        onlyOwnTherapists();
        var start = at(day.plusWeeks(2), 14);
        long a = slot(room(), 1, start), b = slot(room(), 1, start);
        therapist("Asha"); therapist("Bela");
        var pair = List.of(guest("Me", 1), guest("Ravi", 1));
        var group = ok(customer(post("/api/booking-groups"), order(start, "OFFLINE", pair)));
        long groupId = group.get("groupId").asLong();
        assertFalse(offers(day.plusWeeks(2), 14, pair));
        var mine = ok(customer(get("/api/me/booking-groups"), null));
        assertTrue(java.util.stream.StreamSupport.stream(mine.spliterator(), false).anyMatch(g -> g.get("groupId").asLong() == groupId));
        ok(customer(post("/api/booking-groups/" + groupId + "/cancel"), Map.of("expectedRefundCents", 0)));
        em.flush();
        assertEquals(2, jdbc.queryForObject("select count(*) from booking_group_member m join booking x on x.id=m.booking_id where m.group_id=? and x.status='CANCELLED'",
                Integer.class, groupId));
        assertEquals(0, jdbc.queryForObject("select booked_units from slot where id=?", Integer.class, a) + jdbc.queryForObject("select booked_units from slot where id=?", Integer.class, b));
        em.clear();
        assertTrue(offers(day.plusWeeks(2), 14, pair), "both rooms are offered again");
    }
}
