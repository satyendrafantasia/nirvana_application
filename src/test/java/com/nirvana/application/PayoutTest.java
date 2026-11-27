package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.payout.PayoutMath;
import com.nirvana.application.service.payout.PayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.StreamSupport;

import static com.nirvana.application.PayoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Settlement in manual mode (no Razorpay keys): statements, idempotent re-runs, bank details, admin close-out. */
@SpringBootTest @AutoConfigureMockMvc
class PayoutTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PayoutService payouts;
    @Autowired com.nirvana.application.rewards.BookingWallet wallet;
    @Autowired com.nirvana.application.repository.BookingRepository bookings;
    PayoutFixtures f;

    @BeforeEach void setUp() {
        f = new PayoutFixtures(jdbc, mvc, json);
        jdbc.update("UPDATE spa SET commission_pct=10 WHERE id=1");
        // Settle whatever the seed data holds, so the runs below see only this test's money.
        payouts.run(now());
    }

    @Test void theMathsMatchTheDashboardEstimate() {
        var split = PayoutMath.split(180_000, 25_000, 10);
        assertEquals(155_000, split.online());
        assertEquals(15_500, split.commission());
        assertEquals(139_500, split.net());
        // Refunds larger than takings: no commission, and a negative amount that's carried forward.
        assertEquals(0, PayoutMath.split(0, 100_000, 10).commission());
        assertEquals(-100_000, PayoutMath.split(0, 100_000, 10).net());
    }

    @Test @Transactional void aRunPaysOnlineMoneyOnceLessRefundsAndCommissionAndNeverDeskMoney() throws Exception {
        long a = f.booking(), b = f.booking(), c = f.booking();
        long payA = f.payment(a, 100_000, daysAgo(3));
        long payB = f.payment(b, 50_000, daysAgo(3));
        f.refund(payB, 20_000, daysAgo(2));
        long pkg = f.packageSale("300.00", daysAgo(3));
        f.packageRefund(pkg, 5_000, daysAgo(2));
        f.deskReceipt(a, 99_999, daysAgo(3)); // the spa's own money: never in a payout
        f.payment(c, 70_000, now().minusHours(1)); // after the cut-off: waits for the next run

        int before = f.payoutsFor(1);
        payouts.run(daysAgo(1));
        assertEquals(before + 1, f.payoutsFor(1));
        var p = f.latest();
        assertEquals(180_000L, ((Number) p.get("gross_cents")).longValue());
        assertEquals(25_000L, ((Number) p.get("refunds_cents")).longValue());
        assertEquals(15_500L, ((Number) p.get("commission_cents")).longValue());
        assertEquals(139_500, ((Number) p.get("amount_cents")).intValue());
        assertEquals("PENDING", p.get("status"));
        assertEquals("Waiting for the spa's bank details", p.get("failure_reason"));
        long id = ((Number) p.get("id")).longValue();
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE payout_id=?", Integer.class, id));

        // Re-running, through the admin "run now", never pays the same money twice.
        ok(f.admin(post("/api/admin/payouts/run").param("until", daysAgo(2).toLocalDate().toString()), null));
        payouts.run(daysAgo(1));
        assertEquals(before + 1, f.payoutsFor(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE kind='BOOKING' AND source_id=?", Integer.class, payA));

        // The owner's view: the statement, and what's waiting for the next run.
        var overview = ok(f.owner(get("/api/owner/spas/1/payouts"), null));
        assertFalse(overview.get("automatic").asBoolean());
        assertEquals(id, overview.at("/items/0/id").asLong());
        assertEquals(139_500, overview.at("/items/0/net").asInt());
        assertEquals(70_000, overview.at("/upcoming/0/gross").asLong());
        assertEquals(63_000, overview.at("/upcoming/0/net").asLong());
        var statement = ok(f.owner(get("/api/owner/spas/1/payouts/" + id), null));
        assertEquals(5, statement.get("items").size());
        assertTrue(StreamSupport.stream(statement.get("items").spliterator(), false).anyMatch(i -> i.get("amount").asLong() == -20_000));
        assertEquals(404, f.call(get("/api/owner/spas/2/payouts/" + id), 2, "ROLE_ADMIN", null).status());

        // Bank details: validated, and only the last four digits are kept.
        assertEquals(400, f.owner(put("/api/owner/spas/1/payouts/account"),
                Map.of("accountHolder", "Lotus Wellness", "ifsc", "HDFC123", "accountNumber", "123456789012")).status());
        var account = ok(f.owner(put("/api/owner/spas/1/payouts/account"),
                Map.of("accountHolder", "Lotus  Wellness", "ifsc", "hdfc0001234", "accountNumber", "1234 5678 9012", "businessType", "partnership")));
        assertEquals("XXXX9012", account.get("accountMasked").asText());
        assertEquals("HDFC0001234", account.get("ifsc").asText());
        assertEquals("NOT_LINKED", account.get("routeStatus").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM spa_payout_account WHERE CONCAT_WS('|',account_holder,ifsc,account_last4,route_account_id) LIKE '%12345678%'", Integer.class));

        // Manual mode: a retry can't transfer, it says what it's waiting for; the admin records the bank transfer.
        var retried = ok(f.admin(post("/api/admin/payouts/" + id + "/retry"), null));
        assertEquals("PENDING", retried.get("status").asText());
        assertEquals("Waiting for a manual bank transfer", retried.get("failureReason").asText());
        assertEquals(400, f.admin(post("/api/admin/payouts/" + id + "/mark-paid"), Map.of("reference", "x")).status());
        var paid = ok(f.admin(post("/api/admin/payouts/" + id + "/mark-paid"), Map.of("reference", "UTR2026112700123")));
        assertEquals("COMPLETED", paid.get("status").asText());
        assertEquals("UTR2026112700123", paid.get("transferId").asText());
        assertEquals(409, f.admin(post("/api/admin/payouts/" + id + "/mark-paid"), Map.of("reference", "UTR2026112700999")).status());
        assertEquals(409, f.admin(post("/api/admin/payouts/" + id + "/retry"), null).status());
        var list = ok(f.admin(get("/api/admin/payouts").param("status", "COMPLETED").param("spa", "1"), null));
        assertEquals("MANUAL", list.get("mode").asText());
        assertEquals(id, list.at("/items/0/id").asLong());
        assertEquals("XXXX9012", ok(f.admin(get("/api/admin/payouts/" + id), null)).at("/account/accountMasked").asText());
    }

    @Test @Transactional void walletRefundAndSpendSettleAtTheirOwnSpasAndOnlyOnce() {
        String name = "Wallet destination " + java.util.UUID.randomUUID();
        jdbc.update("""
            INSERT INTO spa(created_at,updated_at,name,commission_pct,default_currency,is_active,is_featured,is_verified,kyc_status,tax_percent,timezone)
            VALUES(NOW(),NOW(),?,20,'INR',true,false,true,'APPROVED',0,'Asia/Kolkata')
            """, name);
        long destination = jdbc.queryForObject("SELECT id FROM spa WHERE name=?", Long.class, name);
        long original = f.booking(), visit = f.booking();
        // Seed bookings use the local spa's currency, which can differ from this INR payment fixture.
        jdbc.update("UPDATE booking SET currency='INR' WHERE id IN (?,?)", original, visit);
        jdbc.update("UPDATE booking SET spa_id=? WHERE id=?", destination, visit);
        long payment = f.payment(original, 20_000, daysAgo(5));
        var first = bookings.findById(original).orElseThrow();
        var next = bookings.findById(visit).orElseThrow();
        wallet.credit(first, "INR", 20_000);
        // Cancellation posts this original-payment reversal through Payment.setRefundedCents.
        f.refund(payment, 20_000, daysAgo(4));
        wallet.spend(next, 15_000);
        assertEquals(7_500, wallet.returnForBooking(next, 50));
        assertEquals(0, wallet.returnForBooking(next, 50));
        jdbc.update("UPDATE booking_wallet_spend SET spent_at=?,returned_at=? WHERE booking_id=?", daysAgo(3),daysAgo(2),visit);

        var cutoff = daysAgo(1);
        payouts.run(cutoff);
        payouts.run(cutoff);
        var origin = f.latest();
        assertEquals(0, ((Number)origin.get("amount_cents")).intValue());
        var target = jdbc.queryForMap("""
            SELECT p.* FROM payout p JOIN payout_item i ON i.payout_id=p.id
            WHERE i.kind='WALLET' AND i.source_id=?
            """, visit);
        assertEquals(destination, ((Number)target.get("spa_id")).longValue());
        assertEquals(15_000, ((Number)target.get("gross_cents")).intValue());
        assertEquals(7_500, ((Number)target.get("refunds_cents")).intValue());
        assertEquals(1_500, ((Number)target.get("commission_cents")).intValue());
        assertEquals(6_000, ((Number)target.get("amount_cents")).intValue());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE payout_id=?",Integer.class,target.get("id")));
    }

    @Test @Transactional void refundsBiggerThanTakingsAreCarriedToTheNextPayout() {
        long a = f.booking(), d = f.booking();
        long payA = f.payment(a, 100_000, daysAgo(5));
        payouts.run(daysAgo(4));
        long first = ((Number) f.latest().get("id")).longValue();
        int count = f.payoutsFor(1);

        f.refund(payA, 100_000, daysAgo(3));
        payouts.run(daysAgo(2));
        assertEquals(count, f.payoutsFor(1), "nothing owed: no payout, the refund waits");

        f.payment(d, 150_000, daysAgo(2));
        var cut = daysAgo(1);
        payouts.run(cut);
        var p = f.latest();
        assertNotEquals(first, ((Number) p.get("id")).longValue());
        assertEquals(150_000L, ((Number) p.get("gross_cents")).longValue());
        assertEquals(100_000L, ((Number) p.get("refunds_cents")).longValue());
        assertEquals(45_000, ((Number) p.get("amount_cents")).intValue());
        assertEquals(cut, jdbc.queryForObject("SELECT period_end FROM payout WHERE id=?", java.time.LocalDateTime.class, p.get("id")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE payout_id=?", Integer.class, p.get("id")));
    }

    /** Payouts and the manual bank statement (Payments, Payout statements) never settle the same business day. */
    @Test @Transactional void daysOnAManualBankStatementAreNotPaidAgainAndViceVersa() throws Exception {
        var zone = java.time.ZoneId.of(jdbc.queryForObject("SELECT COALESCE(NULLIF(timezone,''),'Asia/Kolkata') FROM spa WHERE id=1", String.class));
        var manualDay = java.time.LocalDate.now(zone).minusDays(6);
        var manualAt = manualDay.atTime(12, 0).atZone(zone).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        long a = f.booking(), b = f.booking();
        long payA = f.payment(a, 40_000, manualAt);
        long payB = f.payment(b, 60_000, daysAgo(3));
        String manualRef = "NEFT-" + java.util.UUID.randomUUID().toString().substring(0, 10);
        java.util.function.Function<java.time.LocalDate, Map<String, Object>> statement = d -> {
            var m = new java.util.HashMap<String, Object>(Map.of("date", d.toString(), "periodStart", d.toString(), "currency", "INR",
                    "cash", 0, "card", 0, "upi", 0, "transferred", 36_000, "transferDate", d.toString(), "adjustment", 0));
            m.put("reference", manualRef);
            return m;
        };
        ok(f.admin(post("/api/owner/spas/1/finance/PAYOUT"), statement.apply(manualDay)));

        payouts.run(daysAgo(1));
        var p = f.latest();
        long id = ((Number) p.get("id")).longValue();
        assertEquals(60_000L, ((Number) p.get("gross_cents")).longValue(), "the manually settled day is left out");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE kind='BOOKING' AND source_id=?", Integer.class, payA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payout_item WHERE kind='BOOKING' AND source_id=?", Integer.class, payB));
        var payoutDay = daysAgo(3).atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        assertEquals(id, jdbc.queryForObject("SELECT payout_id FROM payout_business_day WHERE spa_id=1 AND currency='INR' AND covered_date=?", Long.class, payoutDay));

        // The payout's day can't then go on a manual statement; one bank reference settles one statement.
        var overlap = statement.apply(payoutDay);
        overlap.put("reference", manualRef + "-2");
        assertNotEquals(200, f.admin(post("/api/owner/spas/1/finance/PAYOUT"), overlap).status());
        assertEquals(409, f.admin(post("/api/admin/payouts/" + id + "/mark-paid"), Map.of("reference", manualRef)).status());
        assertEquals("PENDING", payouts.payout(id).get("status"));
        ok(f.admin(post("/api/admin/payouts/" + id + "/mark-paid"), Map.of("reference", manualRef + "-3")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM spa_transfer_reference WHERE reference_no=?", Integer.class, manualRef + "-3"));
        var unmatched = ok(f.admin(get("/api/owner/spas/1/finance/unmatched").param("through", java.time.LocalDate.now(zone).toString()).param("currency", "INR"), null));
        assertFalse(unmatched.toString().contains(payoutDay.toString()), "a paid payout's days count as matched");
    }

    @Test void onlyTheOwnerAndAdminsSeePayouts() throws Exception {
        assertEquals(403, f.call(get("/api/owner/spas/1/payouts"), 3, "ROLE_USER", null).status());
        assertEquals(403, f.call(get("/api/owner/spas/1/payouts"), 3, "ROLE_SPA_STAFF", null).status());
        assertEquals(403, f.owner(get("/api/admin/payouts"), null).status());
        assertEquals(403, f.owner(post("/api/admin/payouts/run"), null).status());
        assertEquals(400, f.admin(get("/api/admin/payouts").param("status", "BOGUS"), null).status());
        assertEquals(400, f.admin(post("/api/admin/payouts/run").param("until", now().toLocalDate().plusDays(3).toString()), null).status());
    }
}
