package com.nirvana.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Online money for spa 1 (owner: user 2), written straight to the tables the settlement run reads. */
class PayoutFixtures {
    record Reply(int status, JsonNode body) {}
    final JdbcTemplate jdbc;
    final MockMvc mvc;
    final ObjectMapper json;

    PayoutFixtures(JdbcTemplate jdbc, MockMvc mvc, ObjectMapper json) { this.jdbc = jdbc; this.mvc = mvc; this.json = json; }

    static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).withNano(0); }
    static LocalDateTime daysAgo(int days) { return now().minusDays(days); }

    Reply call(MockHttpServletRequestBuilder req, long user, String role, Object body) throws Exception {
        var principal = new UserPrincipal(user, "payouts", "payouts@example.test", "", true, List.of(new SimpleGrantedAuthority(role)));
        req.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(req).andReturn().getResponse();
        String text = response.getContentAsString();
        return new Reply(response.getStatus(), text.isBlank() ? null : json.readTree(text));
    }
    Reply owner(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, 2, "ROLE_SPA_OWNER", body); }
    Reply admin(MockHttpServletRequestBuilder req, Object body) throws Exception { return call(req, 2, "ROLE_ADMIN", body); }
    static JsonNode ok(Reply r) { assertEquals(200, r.status(), () -> String.valueOf(r.body())); return r.body(); }

    /** A copy of one of spa 1's bookings, so it can carry its own payment. */
    long booking() {
        var cols = String.join(",", jdbc.queryForList("""
            SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='booking'
              AND COLUMN_NAME NOT IN ('id','booking_reference') ORDER BY ORDINAL_POSITION""", String.class));
        String ref = "PAYOUT-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO booking(" + cols + ",booking_reference) SELECT " + cols + ",? FROM booking WHERE spa_id=1 ORDER BY id LIMIT 1", ref);
        return jdbc.queryForObject("SELECT id FROM booking WHERE booking_reference=?", Long.class, ref);
    }

    /** An online booking payment captured at {@code at}; returns the payment id. */
    long payment(long booking, int cents, LocalDateTime at) {
        String txn = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        jdbc.update("""
            INSERT INTO payment(created_at,updated_at,amount_cents,currency,fee_cents,gateway,payment_status,refunded_cents,total_price,
              transaction_id,booking_id,captured_at,version) VALUES(?,?,?,'INR',0,'razorpay','CAPTURED',0,?,?,?,?,0)
            """, at, at, cents, cents / 100.0, txn, booking, at);
        return jdbc.queryForObject("SELECT id FROM payment WHERE transaction_id=?", Long.class, txn);
    }

    void refund(long payment, int cents, LocalDateTime at) {
        Integer before = jdbc.queryForObject("SELECT COALESCE(MAX(cumulative_cents),0) FROM booking_refund_posting WHERE payment_id=?", Integer.class, payment);
        jdbc.update("INSERT INTO booking_refund_posting(payment_id,cumulative_cents,amount_cents,recorded_at) VALUES(?,?,?,?)", payment, before + cents, cents, at);
        jdbc.update("UPDATE payment SET payment_status='PARTIALLY_REFUNDED',refunded_cents=? WHERE id=?", before + cents, payment);
    }

    /** A package sold online for spa 1 (a copy of any seeded subscription); returns its id. */
    long packageSale(String rupees, LocalDateTime at) {
        var cols = String.join(",", jdbc.queryForList("""
            SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_spa_package_subscription'
              AND COLUMN_NAME NOT IN ('id','spa_id','price_paid','payment_status','collected_at','purchase_date','razorpay_order_id','currency','payment_reference_id')
            ORDER BY ORDINAL_POSITION""", String.class));
        String ref = "pkg_" + UUID.randomUUID().toString().substring(0, 12);
        int n = jdbc.update("INSERT INTO user_spa_package_subscription(" + cols + ",spa_id,price_paid,payment_status,collected_at,purchase_date,currency,payment_reference_id) SELECT "
                + cols + ",1,?,'SUCCESS',?,?,'INR',? FROM user_spa_package_subscription ORDER BY id LIMIT 1", new java.math.BigDecimal(rupees), at, at, ref);
        assertEquals(1, n, "needs a seeded package subscription to copy");
        return jdbc.queryForObject("SELECT id FROM user_spa_package_subscription WHERE payment_reference_id=?", Long.class, ref);
    }

    void packageRefund(long subscription, int cents, LocalDateTime at) {
        jdbc.update("INSERT INTO spa_package_refund_posting(subscription_id,cumulative_cents,amount_cents,recorded_at) VALUES(?,?,?,?)", subscription, cents, cents, at);
    }

    void deskReceipt(long booking, int cents, LocalDateTime at) {
        jdbc.update("INSERT INTO spa_desk_receipt(booking_id,spa_id,collected_by,collected_at,amount_cents,currency,method) VALUES(?,1,2,?,?,'INR','CASH')", booking, at, cents);
    }

    /** The newest payout for spa 1. */
    java.util.Map<String, Object> latest() {
        return jdbc.queryForMap("SELECT * FROM payout WHERE spa_id=1 AND period_end IS NOT NULL ORDER BY id DESC LIMIT 1");
    }
    int payoutsFor(long spa) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payout WHERE spa_id=? AND period_end IS NOT NULL", Integer.class, spa);
    }
}
