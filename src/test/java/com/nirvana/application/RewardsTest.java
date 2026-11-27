package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.rewards.GiftCardService;
import com.nirvana.application.rewards.RewardRules;
import com.nirvana.application.security.UserPrincipal;
import com.nirvana.application.service.impl.LoyaltyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Loyalty ledger, gift cards and referrals through the real checkout. Spa 1 sells treatment 1 for
 * Rs 120 with 18% tax; a Monday 10:00 slot gets the 5% off-peak discount, so a plain booking is
 * 12,000 - 600 + 2,052 tax = 13,452 paise.
 */
@SpringBootTest(properties = "reliability.rate-limiting.enabled=false")
@AutoConfigureMockMvc @Transactional
class RewardsTest {
    static final String SCHEDULE = "/api/owner/spas/1/schedule";
    static final ZoneId ZONE = ZoneId.of("Europe/London");
    static final int PLAIN_TOTAL = 13_452;
    static final long CUSTOMER = 3, FRIEND = 4;
    final LocalDate day = LocalDate.now(ZONE).plusMonths(4).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired RewardRules rules;
    @Autowired LoyaltyService loyalty;
    @Autowired GiftCardService giftCards;
    @Autowired BookingRepository bookings;
    int savedRate, savedCap;

    @BeforeEach void setUp() {
        savedRate = rules.pointsPer100Rupees;
        savedCap = rules.referralMonthlyCap;
        rules.pointsPer100Rupees = 10; // 13,452 paise earns 10 points: easier to see than 1
        jdbc.update("update app_user set loyalty_points=0, referral_code=null where id in (3,4)");
        jdbc.update("delete from loyalty_ledger where user_id in (3,4)");
        // Only a spa registered for GST may charge the 18% these totals include (see GstInvoiceTest).
        jdbc.update("update spa set gstin='09AABCF1234B1ZA', tax_percent=18 where id=1");
    }

    @AfterEach void restore() {
        rules.pointsPer100Rupees = savedRate;
        rules.referralMonthlyCap = savedCap;
    }

    record Reply(int status, JsonNode body) {}

    Reply call(MockHttpServletRequestBuilder req, long user, String role, Object body) throws Exception {
        var principal = new UserPrincipal(user, "rewards", "rewards@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        req.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(req).andReturn().getResponse();
        String text = response.getContentAsString();
        return new Reply(response.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
    Reply admin(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, 1, "ROLE_PLATFORM_ADMIN", body); }
    void sync() { em.flush(); em.clear(); }
    int points(long user) { sync(); return jdbc.queryForObject("select loyalty_points from app_user where id=?", Integer.class, user); }
    int bookingInt(long booking, String column) { sync(); return jdbc.queryForObject("select " + column + " from booking where id=?", Integer.class, booking); }
    String bookingStatus(long booking) { sync(); return jdbc.queryForObject("select status from booking where id=?", String.class, booking); }
    int ledgerSum(long user) { sync(); return jdbc.queryForObject("select coalesce(sum(points),0) from loyalty_ledger where user_id=?", Integer.class, user); }

    /** A credit lot, as the ledger would write it. */
    void grant(long user, int points, String expiresIn) {
        sync();
        jdbc.update("insert into loyalty_ledger(user_id,entry_type,points,balance_after,remaining,expires_at,note,created_at)"
                + " select id,'ADJUSTMENT',?,loyalty_points+?,?,utc_timestamp(6) + interval " + expiresIn + ",'test',utc_timestamp(6) from app_user where id=?",
                points, points, points, user);
        jdbc.update("update app_user set loyalty_points=loyalty_points+? where id=?", points, user);
    }

    long slot(int hour) throws Exception {
        var roster = call(put(SCHEDULE + "/therapists/1/days/" + day), 2, "ROLE_SPA_OWNER", Map.of("intervals", List.of(Map.of("kind", "WORK",
                "start", day.atTime(9, 0).atZone(ZONE).toOffsetDateTime().toString(),
                "end", day.atTime(18, 0).atZone(ZONE).toOffsetDateTime().toString()))));
        assertEquals(200, roster.status(), () -> roster.body().toString());
        var room = call(post(SCHEDULE + "/rooms"), 2, "ROLE_SPA_OWNER", Map.of("name", "Rewards room " + hour, "active", true));
        assertEquals(200, room.status(), () -> room.body().toString());
        var created = call(post(SCHEDULE + "/slots"), 2, "ROLE_SPA_OWNER", Map.of("roomId", room.body().get("id").asLong(), "serviceId", 1,
                "start", day.atTime(hour, 0).atZone(ZONE).toOffsetDateTime().toString(),
                "end", day.atTime(hour + 1, 0).atZone(ZONE).toOffsetDateTime().toString(), "capacity", 1));
        assertEquals(200, created.status(), () -> created.body().toString());
        return created.body().get("id").asLong();
    }
    Reply book(long user, long slot, Map<String, Object> extra) throws Exception {
        sync();
        var body = new HashMap<String, Object>(Map.of("slotId", slot, "spaId", 1, "serviceId", 1, "guests", 1, "paymentMode", "OFFLINE"));
        body.putAll(extra);
        return call(post("/api/bookings"), user, "ROLE_USER", body);
    }
    long booked(Reply reply) {
        assertEquals(2, reply.status() / 100, () -> reply.body().toString());
        return reply.body().get("bookingId").asLong();
    }
    Reply spaCancel(long booking) throws Exception {
        return call(post("/api/owner/spas/1/bookings/" + booking + "/cancel"), 2, "ROLE_SPA_OWNER", Map.of("reason", "Therapist is unwell"));
    }
    /** Past appointment, paid in full at the desk, completed by the spa. */
    void completePaid(long booking) throws Exception {
        sync();
        jdbc.update("update booking set start_ts = now() - interval 3 day, end_ts = now() - interval 3 day + interval 1 hour where id=?", booking);
        int due = bookingInt(booking, "remainder_cents");
        if (due > 0) jdbc.update("insert into spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method)"
                + " values(?,1,2,utc_timestamp(6),?,'INR','CASH')", booking, due);
        var done = call(post("/api/owner/spas/1/bookings/" + booking + "/complete"), 2, "ROLE_SPA_OWNER", null);
        assertEquals(200, done.status(), () -> done.body().toString());
    }
    String issueCard(int amountCents, Long spaId) throws Exception {
        var input = new HashMap<String, Object>(Map.of("amountCents", amountCents, "recipientEmail", "friend@example.test", "note", "Goodwill"));
        if (spaId != null) input.put("spaId", spaId);
        var issued = admin(post("/api/admin/gift-cards"), input);
        assertEquals(200, issued.status(), () -> issued.body().toString());
        return issued.body().get("code").asText();
    }
    Map<String, Object> card(String code) { sync(); return jdbc.queryForMap("select id,status,balance_cents from gift_card where code=?", code); }

    // ------------------------------------------------------------------ loyalty ledger

    @Test void pointsAreSpentFromTheEarliestLotFirstAndComeBackWhenTheBookingIsCancelled() throws Exception {
        grant(CUSTOMER, 500, "2 month");
        grant(CUSTOMER, 200, "11 month");
        var reply = book(CUSTOMER, slot(10), Map.of("redeemLoyaltyPoints", true));
        long booking = booked(reply);
        // 20% of what is left after the off-peak discount (11,400) is 2,280 paise: 22 whole points.
        assertEquals(22, bookingInt(booking, "loyalty_points_redeemed"));
        assertEquals(600 + 2_200, bookingInt(booking, "discount_cents"));
        assertEquals(22, reply.body().get("loyaltyPointsRedeemed").asInt());
        assertEquals(678, points(CUSTOMER));
        assertEquals(points(CUSTOMER), ledgerSum(CUSTOMER), "the ledger adds up to the balance");
        var lots = jdbc.queryForList("select remaining from loyalty_ledger where user_id=3 and entry_type='ADJUSTMENT' order by expires_at", Integer.class);
        assertEquals(List.of(478, 200), lots, "the lot that expires first is used first");

        assertEquals(200, spaCancel(booking).status());
        assertEquals(700, points(CUSTOMER));
        assertEquals(700, ledgerSum(CUSTOMER));
        var returned = jdbc.queryForMap("select points, remaining, expires_at > utc_timestamp() + interval 29 day as later from loyalty_ledger"
                + " where booking_id=? and entry_type='REDEEM_RETURN'", booking);
        assertEquals(22, ((Number) returned.get("points")).intValue());
        assertEquals(1, ((Number) returned.get("later")).intValue(), "returned points stay usable for a while");
        assertEquals(400, book(CUSTOMER, slot(12), Map.of("couponCode", "NOSUCH", "redeemLoyaltyPoints", true)).status());
        assertEquals(700, points(CUSTOMER), "a failed checkout spends nothing");
    }

    @Test void onlyPaidCompletedBookingsEarnAndARefundTakesThePointsBack() throws Exception {
        long paid = booked(book(CUSTOMER, slot(10), Map.of()));
        long unpaid = booked(book(CUSTOMER, slot(12), Map.of()));
        completePaid(paid);
        assertEquals(10, bookingInt(paid, "loyalty_points_earned"));
        assertEquals(10, points(CUSTOMER));
        var earn = jdbc.queryForMap("select points, expires_at between utc_timestamp() + interval 11 month and utc_timestamp() + interval 13 month as year"
                + " from loyalty_ledger where booking_id=? and entry_type='EARN'", paid);
        assertEquals(10, ((Number) earn.get("points")).intValue());
        assertEquals(1, ((Number) earn.get("year")).intValue(), "earned points expire after 12 months");

        call(post("/api/owner/spas/1/bookings/" + paid + "/complete"), 2, "ROLE_SPA_OWNER", null);
        loyalty.syncEarnedForBooking(paid);
        assertEquals(10, points(CUSTOMER), "completing or re-checking again earns nothing more");

        sync();
        jdbc.update("update booking set start_ts = now() - interval 3 day, end_ts = now() - interval 3 day + interval 1 hour where id=?", unpaid);
        assertEquals(200, call(post("/api/owner/spas/1/bookings/" + unpaid + "/complete"), 2, "ROLE_SPA_OWNER", null).status());
        assertEquals(0, bookingInt(unpaid, "loyalty_points_earned"), "nothing collected, nothing earned");

        // The desk gives the money back: the points go with it.
        jdbc.update("update spa_desk_receipt set returned_at=utc_timestamp(6), returned_by=2 where booking_id=?", paid);
        loyalty.syncEarnedForBooking(paid);
        assertEquals(0, bookingInt(paid, "loyalty_points_earned"));
        assertEquals(0, points(CUSTOMER));
        assertEquals(-10, jdbc.queryForObject("select points from loyalty_ledger where booking_id=? and entry_type='EARN_REVERSAL'", Integer.class, paid));
        assertEquals(0, ledgerSum(CUSTOMER));
    }

    @Test void expiredPointsLeaveTheBalanceAndShowInTheHistory() throws Exception {
        grant(CUSTOMER, 50, "-1 day");
        grant(CUSTOMER, 30, "20 day");
        var me = call(get("/api/me/loyalty"), CUSTOMER, "ROLE_USER", null);
        assertEquals(200, me.status(), () -> me.body().toString());
        assertEquals(30, me.body().get("balance").asInt());
        assertEquals(30, me.body().get("expiringSoonPoints").asInt());
        assertEquals("EXPIRY", me.body().get("history").get(0).get("type").asText());
        assertEquals(-50, me.body().get("history").get(0).get("points").asInt());
        assertEquals(30, ledgerSum(CUSTOMER));
        var reply = book(CUSTOMER, slot(10), Map.of("redeemLoyaltyPoints", true));
        assertEquals(22, bookingInt(booked(reply), "loyalty_points_redeemed"), "only unexpired points are spent");
    }

    // ------------------------------------------------------------------ gift cards

    @Test void giftCardPaysAcrossBookingsKeepsTheRestAndComesBackOnCancellation() throws Exception {
        String code = issueCard(50_000, 1L);
        assertEquals("ACTIVE", card(code).get("status"));
        // Online booking fully covered: confirmed at once, nothing sent to Razorpay.
        var first = book(CUSTOMER, slot(10), Map.of("paymentMode", "ONLINE", "giftCardCode", code.toLowerCase().replace("-", " ")));
        long firstId = booked(first);
        assertEquals("CONFIRMED", first.body().get("status").asText());
        assertEquals(PLAIN_TOTAL, first.body().get("giftCardCents").asInt());
        assertEquals(0, first.body().get("totalCents").asInt());
        assertTrue(first.body().get("razorpay") == null || first.body().get("razorpay").isNull());
        assertEquals(50_000 - PLAIN_TOTAL, ((Number) card(code).get("balance_cents")).intValue());

        jdbc.update("update gift_card set balance_cents=5000 where code=?", code); // as if spent elsewhere
        long second = booked(book(CUSTOMER, slot(12), Map.of("giftCardCode", code)));
        assertEquals(5_000, bookingInt(second, "gift_card_cents"));
        assertEquals(bookingInt(second, "price_cents + tax_cents - discount_cents") - 5_000, bookingInt(second, "remainder_cents"),
                "the rest is paid at the spa");
        assertEquals("EXHAUSTED", card(code).get("status"));
        var empty = book(CUSTOMER, slot(14), Map.of("giftCardCode", code));
        assertEquals(400, empty.status());
        assertTrue(empty.body().get("message").asText().contains("no balance"), () -> empty.body().toString());

        assertEquals(200, spaCancel(second).status());
        assertEquals("ACTIVE", card(code).get("status"));
        assertEquals(5_000, ((Number) card(code).get("balance_cents")).intValue());
        long cardId = ((Number) card(code).get("id")).longValue();
        var types = jdbc.queryForList("select txn_type from gift_card_txn where gift_card_id=? order by id", String.class, cardId);
        assertEquals(List.of("ISSUE", "REDEEM", "REDEEM", "REVERSAL"), types);

        // A late cancellation keeps the fee share of the gift card amount, like a card payment.
        sync();
        var booking = bookings.findById(firstId).orElseThrow();
        var giftReturn = loyalty.giftCardReturnQuote(booking, 50);
        assertEquals(PLAIN_TOTAL / 2, giftReturn.refund());
        assertEquals(PLAIN_TOTAL - giftReturn.refund(), giftReturn.fee());
        loyalty.returnRedeemed(booking, 50);
        assertEquals(0, loyalty.giftCardReturnQuote(booking, 50).refund());
        assertEquals(5_000 + PLAIN_TOTAL / 2, ((Number) card(code).get("balance_cents")).intValue());

        var report = call(get("/api/owner/spas/1/gift-cards"), 2, "ROLE_SPA_OWNER", null);
        assertEquals(200, report.status(), () -> report.body().toString());
        assertEquals(1, report.body().get("sales").get("cards").asInt());
        assertEquals(PLAIN_TOTAL - PLAIN_TOTAL / 2, report.body().get("redemptions").get("redeemedCents").asInt(),
                "net of what was returned: 13,452 + 5,000 - 5,000 - 6,726");
        assertEquals(403, call(get("/api/owner/spas/1/gift-cards"), CUSTOMER, "ROLE_USER", null).status());
    }

    @Test void giftCardsAreIssuedAndVoidedByStaffOnlyAndRespectTheirSpaAndExpiry() throws Exception {
        var refused = call(post("/api/admin/gift-cards"), CUSTOMER, "ROLE_USER",
                Map.of("amountCents", 50_000, "recipientEmail", "x@example.test", "note", "free money"));
        assertEquals(403, refused.status());
        assertEquals(4, admin(post("/api/admin/gift-cards"), Map.of("amountCents", 100, "recipientEmail", "x@example.test", "note", "n")).status() / 100,
                "below the minimum amount");

        String code = issueCard(50_000, 1L);
        assertEquals(1, jdbc.queryForObject("select count(*) from gift_card where code=? and expires_at >= utc_timestamp() + interval 364 day",
                Integer.class, code), "valid for at least a year");
        var otherSpa = assertThrows(ResponseStatusException.class, () -> giftCards.quote(code, 999, 10_000));
        assertTrue(otherSpa.getReason().contains("only be used at"));

        long id = ((Number) card(code).get("id")).longValue();
        assertEquals(403, call(post("/api/admin/gift-cards/" + id + "/void"), CUSTOMER, "ROLE_USER", Map.of("reason", "x")).status());
        var voided = admin(post("/api/admin/gift-cards/" + id + "/void"), Map.of("reason", "Issued by mistake"));
        assertEquals(200, voided.status(), () -> voided.body().toString());
        assertEquals("VOID", voided.body().get("status").asText());
        assertEquals(0, voided.body().get("balanceCents").asInt());
        assertEquals(400, book(CUSTOMER, slot(10), Map.of("giftCardCode", code)).status());

        String expiring = issueCard(50_000, null);
        jdbc.update("update gift_card set expires_at=utc_timestamp() - interval 1 minute where code=?", expiring);
        var balance = call(get("/api/v1/gift-cards/balance").param("code", expiring), CUSTOMER, "ROLE_USER", null);
        assertEquals("EXPIRED", balance.body().get("status").asText());
        assertEquals(400, book(CUSTOMER, slot(12), Map.of("giftCardCode", expiring)).status());
        long expId = ((Number) card(expiring).get("id")).longValue();
        assertTrue(giftCards.expire(expId));
        assertEquals("EXPIRED", card(expiring).get("status"));
        assertEquals(-50_000, jdbc.queryForObject("select amount_cents from gift_card_txn where gift_card_id=? and txn_type='EXPIRE'", Integer.class, expId));
    }

    @Test void boughtGiftCardActivatesOnlyOnAVerifiedCaptureAndEmailsTheCode() throws Exception {
        var gateway = new com.razorpay.RazorpayClient("key", "secret");
        gateway.orders = org.mockito.Mockito.mock(com.razorpay.OrderClient.class);
        gateway.payments = org.mockito.Mockito.mock(com.razorpay.PaymentClient.class);
        org.mockito.Mockito.when(gateway.orders.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.razorpay.Order(new org.json.JSONObject().put("id", "order_gift_1")));
        var remote = new org.json.JSONObject().put("id", "pay_gift_1").put("order_id", "order_gift_1").put("amount", 100_000)
                .put("currency", "INR").put("status", "captured").put("captured", true).put("amount_refunded", 0);
        org.mockito.Mockito.when(gateway.payments.fetch("pay_gift_1")).thenAnswer(i -> new com.razorpay.Payment(remote));
        var props = new com.nirvana.application.config.RazorpayProperties();
        props.setEnabled(true); props.setKeyId("key"); props.setWebhookSecret("test-webhook"); props.setKeySecret("secret");
        var mail = org.mockito.Mockito.mock(com.nirvana.application.otp.sender.EmailSender.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.nirvana.application.otp.sender.EmailSender> mailer =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(mailer.getIfAvailable()).thenReturn(mail);
        var service = new GiftCardService(jdbc, rules, props, gateway, mailer, "https://nirvana.example");

        var checkout = service.startPurchase(CUSTOMER, new GiftCardService.PurchaseInput(100_000, null, "Asha", "asha@example.test", "Happy birthday"));
        assertEquals("order_gift_1", checkout.razorpayOrderId());
        assertEquals("PAYMENT_PENDING", checkout.card().status());
        assertThrows(ResponseStatusException.class, () -> giftCards.quote(checkout.card().code(), 1, 1000), "unpaid cards cannot be used");
        assertThrows(IllegalArgumentException.class,
                () -> service.startPurchase(CUSTOMER, new GiftCardService.PurchaseInput(2_000_000, null, null, "a@example.test", null)), "over the RBI cap");

        var request = new com.nirvana.application.model.dto.RazorpayConfirmRequest();
        request.setRazorpayOrderId("order_gift_1"); request.setRazorpayPaymentId("pay_gift_1"); request.setRazorpaySignature("forged");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(CUSTOMER, checkout.card().id(), request));
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> service.confirm(FRIEND, checkout.card().id(), request),
                "only the buyer confirms");

        remote.put("amount", 1);
        assertThrows(IllegalStateException.class, () -> service.captured("order_gift_1", "pay_gift_1"), "wrong amount");
        remote.put("amount", 100_000);
        service.captured("order_gift_1", "pay_gift_1"); // webhook
        service.captured("order_gift_1", "pay_gift_1"); // replay
        var card = service.card(checkout.card().id());
        assertEquals("ACTIVE", card.status());
        assertEquals(100_000, card.balanceCents());
        assertEquals(1, jdbc.queryForObject("select count(*) from gift_card_txn where gift_card_id=? and txn_type='ISSUE'", Integer.class, card.id()));
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(1))
                .sendOtpEmail(org.mockito.ArgumentMatchers.eq("asha@example.test"), org.mockito.ArgumentMatchers.anyString(), body.capture());
        assertTrue(body.getValue().contains(card.code()));
        assertTrue(body.getValue().contains("Happy birthday"));
        assertEquals(card.code(), service.purchasedBy(CUSTOMER).get(0).code());

        // Part spent, then the payment is refunded in full: the balance closes.
        jdbc.update("update gift_card set balance_cents=40000 where id=?", card.id());
        remote.put("amount_refunded", 100_000);
        service.refunded("pay_gift_1");
        service.refunded("pay_gift_1");
        card = service.card(card.id());
        assertEquals("REFUNDED", card.status());
        assertEquals(0, card.balanceCents());
        assertEquals(1, jdbc.queryForObject("select count(*) from gift_card_txn where gift_card_id=? and txn_type='REFUND'", Integer.class, card.id()));
    }

    // ------------------------------------------------------------------ checkout

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "No GSTIN,                 ,                  ,    150000, 0,     150000",
            "Invalid GSTIN,            09AABCF1234B1ZB,  12.5, 150004, 0,     150004",
            "Service rate override,    09AABCF1234B1ZA,  12.5, 150004, 18751, 168755",
            "Spa rate fallback,        09AABCF1234B1ZA,      , 150003, 27001, 177004"
    })
    void payAtSpaTaxAgreesAcrossPreviewConfirmationAndOwnerDesk(String scenario, String gstin,
            java.math.BigDecimal serviceRate, int price, int expectedTax, int expectedTotal) throws Exception {
        jdbc.update("update spa set gstin=?, tax_percent=18 where id=1", gstin);
        jdbc.update("update service set price_cents=?, base_price_cents=?, gst_rate_pct=?, currency='INR' where id=1",
                price, price, serviceRate);
        long slotId = slot(14); // No morning discount; amounts are in paise.
        var preview = call(post("/api/v1/checkout/preview"), CUSTOMER, "ROLE_USER",
                Map.of("spaId", 1, "serviceId", 1, "slotId", slotId, "guests", 1));
        assertEquals(200, preview.status(), () -> preview.body().toString());
        assertEquals(expectedTax, preview.body().required("taxCents").intValue());
        assertEquals(expectedTotal, preview.body().required("totalCents").intValue());
        assertEquals(expectedTotal, preview.body().required("payableCents").intValue());

        var confirmation = book(CUSTOMER, slotId, Map.of());
        long id = booked(confirmation);
        assertEquals("CONFIRMED", confirmation.body().required("status").textValue());
        assertEquals("OFFLINE", confirmation.body().required("paymentMode").textValue());
        assertEquals(expectedTax, confirmation.body().required("taxCents").intValue());
        assertEquals(expectedTotal, confirmation.body().required("totalCents").intValue());
        assertEquals(expectedTax, bookingInt(id, "tax_cents"));
        assertEquals(expectedTotal, bookingInt(id, "remainder_cents"));

        var desk = call(get("/api/owner/spas/1/desk/records").param("period", "CUSTOM")
                        .param("from", day.toString()).param("to", day.toString())
                        .param("search", confirmation.body().required("bookingReference").textValue()),
                2, "ROLE_SPA_OWNER", null);
        assertEquals(200, desk.status(), () -> desk.body().toString());
        var items = desk.body().required("items");
        assertEquals(1, items.size(), () -> items.toString());
        assertEquals(id, items.get(0).required("id").longValue());
        assertEquals("DUE_AT_SPA", items.get(0).required("payment").textValue());
        assertEquals(expectedTotal, items.get(0).required("payable").intValue());

        // A later catalogue edit and a part payment must not rewrite the customer's checkout amount.
        jdbc.update("update service set price_cents=? where id=1", price + 1000);
        jdbc.update("insert into spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method)"
                + " values(?,1,2,utc_timestamp(6),100,'INR','CASH')", id);
        em.clear();
        int count = jdbc.queryForObject("select count(*) from booking where user_id=? and status='CONFIRMED'", Integer.class, CUSTOMER);
        var mine = call(get("/api/me/bookings").param("status", "CONFIRMED").param("size", String.valueOf(count)),
                CUSTOMER, "ROLE_USER", null);
        assertEquals(200, mine.status(), () -> mine.body().toString());
        var appointment = java.util.stream.StreamSupport.stream(mine.body().at("/page/content").spliterator(), false)
                .filter(row -> row.required("bookingId").longValue() == id).findFirst().orElseThrow();
        assertEquals(expectedTotal, appointment.required("payAtSpaCheckoutCents").intValue());
        assertEquals("INR", appointment.required("currency").textValue());
    }

    @Test void previewShowsEachStepInOrderAndSpendsNothing() throws Exception {
        grant(CUSTOMER, 700, "6 month");
        String code = issueCard(50_000, null);
        long slot = slot(10);
        var input = new HashMap<String, Object>(Map.of("spaId", 1, "serviceId", 1, "slotId", slot, "guests", 1,
                "redeemLoyaltyPoints", true, "giftCardCode", code, "couponCode", "NOSUCH"));
        var preview = call(post("/api/v1/checkout/preview"), CUSTOMER, "ROLE_USER", input);
        assertEquals(200, preview.status(), () -> preview.body().toString());
        JsonNode p = preview.body();
        assertEquals(12_000, p.get("priceCents").asInt());
        assertEquals(600, p.get("discountCents").asInt(), "off-peak only: the unknown coupon is left out");
        assertFalse(p.get("couponError").asText().isBlank());
        assertEquals(22, p.get("pointsRedeemed").asInt());
        assertEquals(2_200, p.get("pointsCents").asInt());
        assertEquals(Math.round((12_000 - 600 - 2_200) * 0.18), p.get("taxCents").asInt(), "tax after discounts and points");
        int total = 12_000 - 600 - 2_200 + p.get("taxCents").asInt();
        assertEquals(total, p.get("totalCents").asInt());
        assertEquals(total, p.get("giftCardCents").asInt(), "the gift card pays the total after tax");
        assertEquals(0, p.get("payableCents").asInt(), "never below zero");
        assertEquals(700, points(CUSTOMER));
        assertEquals(50_000, ((Number) card(code).get("balance_cents")).intValue());

        input.remove("couponCode");
        var booked = book(CUSTOMER, slot, Map.of("redeemLoyaltyPoints", true, "giftCardCode", code));
        long id = booked(booked);
        assertEquals("CONFIRMED", bookingStatus(id));
        assertEquals(total, bookingInt(id, "gift_card_cents"));
        assertEquals(0, bookingInt(id, "remainder_cents"));
        assertEquals(678, points(CUSTOMER));
    }

    // ------------------------------------------------------------------ referrals

    String referralCode(long user) throws Exception {
        var me = call(get("/api/me/referral"), user, "ROLE_USER", null);
        assertEquals(200, me.status(), () -> me.body().toString());
        assertTrue(me.body().get("link").asText().endsWith("/register?ref=" + me.body().get("code").asText()));
        return me.body().get("code").asText();
    }
    Reply claim(long user, String code) throws Exception {
        return call(post("/api/me/referral/claim"), user, "ROLE_USER", Map.of("code", code));
    }
    void newVerifiedCustomer(long user, boolean verified) {
        sync();
        jdbc.update("delete from referral where referee_id=? or referrer_id=?", user, user);
        jdbc.update("update app_user set created_at=utc_timestamp(6), phone_verified=? where id=?", verified, user);
    }
    Map<String, Object> referralOf(long referee) { sync(); return jdbc.queryForMap("select * from referral where referee_id=?", referee); }

    @Test void bothAreRewardedOnceWhenTheFriendsFirstPaidBookingIsCompleted() throws Exception {
        newVerifiedCustomer(CUSTOMER, true);
        String code = referralCode(FRIEND);
        assertEquals(code, referralCode(FRIEND), "the code stays the same");
        assertEquals(400, claim(FRIEND, code).status(), "no self-referral");
        assertEquals(400, claim(CUSTOMER, "NOPE1234").status());
        assertEquals(200, claim(CUSTOMER, code.toLowerCase()).status());
        assertEquals(400, claim(CUSTOMER, code).status(), "only one code per account");

        long booking = booked(book(CUSTOMER, slot(10), Map.of()));
        completePaid(booking);
        var referral = referralOf(CUSTOMER);
        assertEquals("REWARDED", referral.get("status"));
        assertEquals("0000000003", referral.get("rewarded_phone"));
        assertEquals(10 + 100, points(CUSTOMER), "earned points plus the welcome reward");
        assertEquals(100, points(FRIEND));
        assertEquals(points(FRIEND), ledgerSum(FRIEND));
        loyalty.syncEarnedForBooking(booking);
        assertEquals(100, points(FRIEND), "rewarded once");
        var mine = call(get("/api/me/referral"), FRIEND, "ROLE_USER", null);
        assertEquals(1, mine.body().get("rewarded").asInt());
    }

    @Test void aPhoneNumberIsRewardedOnlyOnce() throws Exception {
        newVerifiedCustomer(CUSTOMER, true);
        jdbc.update("delete from referral where referee_id in (1,2)");
        jdbc.update("insert into referral(referrer_id,referee_id,code,status,rewarded_phone,rewarded_at,created_at)"
                + " values(1,2,'OLDCODE1','REWARDED','0000000003',utc_timestamp(6),utc_timestamp(6))");
        assertEquals(200, claim(CUSTOMER, referralCode(FRIEND)).status());
        completePaid(booked(book(CUSTOMER, slot(10), Map.of())));
        assertEquals("REJECTED", referralOf(CUSTOMER).get("status"));
        assertEquals(0, points(FRIEND));
        assertEquals(10, points(CUSTOMER), "only the points the booking earned");
    }

    @Test void theReferrerIsRewardedForAtMostTheMonthlyCap() throws Exception {
        newVerifiedCustomer(CUSTOMER, true);
        rules.referralMonthlyCap = 1;
        jdbc.update("delete from referral where referee_id in (1,2)");
        jdbc.update("insert into referral(referrer_id,referee_id,code,status,rewarded_phone,referrer_points,rewarded_at,created_at)"
                + " values(4,1,'X','REWARDED','9999999991',100,utc_timestamp(6),utc_timestamp(6))");
        assertEquals(200, claim(CUSTOMER, referralCode(FRIEND)).status());
        completePaid(booked(book(CUSTOMER, slot(10), Map.of())));
        var referral = referralOf(CUSTOMER);
        assertEquals("REWARDED", referral.get("status"));
        assertEquals(0, ((Number) referral.get("referrer_points")).intValue());
        assertEquals(100, ((Number) referral.get("referee_points")).intValue(), "the friend still gets their reward");
        assertTrue(String.valueOf(referral.get("reason")).contains("limit"));
        assertEquals(0, points(FRIEND));
    }

    @Test void anUnverifiedPhoneWaitsAndOldAccountsCannotAddACode() throws Exception {
        newVerifiedCustomer(CUSTOMER, false);
        assertEquals(200, claim(CUSTOMER, referralCode(FRIEND)).status());
        completePaid(booked(book(CUSTOMER, slot(10), Map.of())));
        var referral = referralOf(CUSTOMER);
        assertEquals("PENDING", referral.get("status"));
        assertTrue(String.valueOf(referral.get("reason")).contains("verified phone"));
        assertEquals(0, points(FRIEND));

        jdbc.update("delete from referral where referee_id=?", CUSTOMER);
        jdbc.update("update app_user set created_at=utc_timestamp(6) - interval 90 day where id=?", CUSTOMER);
        var late = claim(CUSTOMER, referralCode(FRIEND));
        assertEquals(400, late.status());
        assertTrue(late.body().get("message").asText().contains("days of joining"), () -> late.body().toString());
    }

    @Test void couponRewardsArePersonal() throws Exception {
        newVerifiedCustomer(CUSTOMER, true);
        var previous = rules.referralReward;
        rules.referralReward = RewardRules.ReferralReward.COUPON;
        try {
            assertEquals(200, claim(CUSTOMER, referralCode(FRIEND)).status());
            completePaid(booked(book(CUSTOMER, slot(10), Map.of())));
            var referral = referralOf(CUSTOMER);
            String friendCoupon = (String) referral.get("referrer_coupon");
            assertNotNull(friendCoupon);
            assertNotNull(referral.get("referee_coupon"));
            var wrongOwner = book(CUSTOMER, slot(12), Map.of("couponCode", friendCoupon));
            assertEquals(400, wrongOwner.status());
            assertTrue(wrongOwner.body().get("message").asText().contains("another account"));
            var own = book(CUSTOMER, slot(14), Map.of("couponCode", (String) referral.get("referee_coupon")));
            assertEquals(2, own.status() / 100, () -> own.body().toString());
            assertTrue(own.body().get("discountCents").asInt() >= rules.referralCouponPaise);
        } finally {
            rules.referralReward = previous;
        }
    }
}
