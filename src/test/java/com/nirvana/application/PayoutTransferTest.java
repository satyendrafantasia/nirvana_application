package com.nirvana.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.service.payout.PayoutProvider;
import com.nirvana.application.service.payout.PayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.nirvana.application.PayoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Automatic transfers through a gateway (a fake Razorpay Route): linking, a failure, the admin retry. */
@SpringBootTest @AutoConfigureMockMvc
class PayoutTransferTest {
    /** Fails the first transfer, then pays. Remembers what it was asked. */
    static class FakeRoute implements PayoutProvider {
        final List<TransferRequest> transfers = new CopyOnWriteArrayList<>();
        final List<LinkRequest> links = new CopyOnWriteArrayList<>();
        @Override public String name() { return "RAZORPAY_ROUTE"; }
        @Override public boolean automatic() { return true; }
        @Override public Transfer transfer(TransferRequest r) {
            transfers.add(r);
            return transfers.size() == 1 ? Transfer.failed("Insufficient balance in Nirvana's account")
                    : new Transfer(State.COMPLETED, "trf_test" + r.payoutId(), null);
        }
        @Override public Transfer status(String id) { return new Transfer(State.COMPLETED, id, null); }
        @Override public Link link(LinkRequest r) { links.add(r); return new Link("acc_TestSpa" + r.spaId(), "acc_prd_Test" + r.spaId(), null); }
    }

    @TestConfiguration static class Config {
        @Bean @Primary FakeRoute fakeRoute() { return new FakeRoute(); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PayoutService payouts;
    @Autowired FakeRoute route;
    PayoutFixtures f;

    @BeforeEach void setUp() {
        f = new PayoutFixtures(jdbc, mvc, json);
        jdbc.update("UPDATE spa SET commission_pct=10 WHERE id=1");
        payouts.run(now());
        // Earlier payouts (seed data, other tests) are not this test's to send.
        jdbc.update("UPDATE payout SET status='COMPLETED' WHERE status<>'COMPLETED'");
        route.transfers.clear();
        route.links.clear();
    }

    @Test @Transactional void aFailedTransferIsRetriedByAnAdminAndPaidOnce() throws Exception {
        // The owner's bank details set up the linked account; the full number goes to the gateway only.
        var account = ok(f.owner(put("/api/owner/spas/1/payouts/account"),
                Map.of("accountHolder", "Lotus Wellness LLP", "ifsc", "ICIC0000042", "accountNumber", "000123456789", "businessType", "llp")));
        assertEquals("LINKED", account.get("routeStatus").asText());
        assertEquals("acc_TestSpa1", account.get("routeAccountId").asText());
        assertEquals("000123456789", route.links.get(0).accountNumber());
        assertFalse(route.links.get(0).toString().contains("000123456789"));

        long b = f.booking();
        f.payment(b, 200_000, daysAgo(3));
        var result = payouts.run(daysAgo(1));
        assertTrue(result.failed() >= 1);
        var p = f.latest();
        long id = ((Number) p.get("id")).longValue();
        assertEquals("FAILED", p.get("status"));
        assertEquals("Insufficient balance in Nirvana's account", p.get("failure_reason"));
        assertEquals(180_000L, route.transfers.get(0).amountCents());
        assertEquals("acc_TestSpa1", route.transfers.get(0).linkedAccountId());

        // Failed payouts are listed for admins, who retry once the balance is topped up.
        var failed = ok(f.admin(get("/api/admin/payouts").param("status", "FAILED"), null));
        assertTrue(failed.get("automatic").asBoolean());
        assertTrue(failed.findValues("id").stream().anyMatch(n -> n.asLong() == id));
        var retried = ok(f.admin(post("/api/admin/payouts/" + id + "/retry"), null));
        assertEquals("COMPLETED", retried.get("status").asText());
        assertEquals("trf_test" + id, retried.get("transferId").asText());
        assertEquals("XXXX6789 · ICIC0000042", retried.get("bankAccount").asText());
        assertEquals(2, retried.get("attempts").asInt());
        // Each attempt has its own idempotency key; a repeat of one attempt would reuse it.
        assertNotEquals(route.transfers.get(0).idempotencyKey(), route.transfers.get(1).idempotencyKey());
        assertTrue(route.transfers.get(1).idempotencyKey().length() <= 36);

        // Paid: no further transfer, whatever runs next.
        assertEquals(409, f.admin(post("/api/admin/payouts/" + id + "/retry"), null).status());
        payouts.run(daysAgo(1));
        payouts.sendWaiting();
        assertEquals(2, route.transfers.stream().filter(t -> t.payoutId() == id).count());
        var statement = ok(f.owner(get("/api/owner/spas/1/payouts/" + id), null));
        assertEquals("COMPLETED", statement.get("status").asText());
        assertEquals(20_000, statement.get("commission").asLong());
    }

    @Test @Transactional void anAdminCanLinkAnAccountMadeOnTheRazorpayDashboard() throws Exception {
        assertEquals(409, f.admin(put("/api/admin/payouts/spas/1/linked-account"), Map.of("accountId", "acc_DashboardMade01")).status());
        jdbc.update("INSERT INTO spa_payout_account(spa_id,account_holder,ifsc,account_last4,updated_at) VALUES(1,'Lotus','HDFC0001234','4321',UTC_TIMESTAMP())");
        assertEquals(400, f.admin(put("/api/admin/payouts/spas/1/linked-account"), Map.of("accountId", "not-an-account")).status());
        var linked = ok(f.admin(put("/api/admin/payouts/spas/1/linked-account"), Map.of("accountId", "acc_DashboardMade01")));
        assertEquals("LINKED", linked.get("routeStatus").asText());
        assertEquals(403, f.owner(put("/api/admin/payouts/spas/1/linked-account"), Map.of("accountId", "acc_DashboardMade02")).status());
    }
}
