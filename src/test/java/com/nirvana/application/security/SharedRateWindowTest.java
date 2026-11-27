package com.nirvana.application.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SharedRateWindowTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void independentWorkersShareOneBudgetAndUseDatabaseExpiry() throws Exception {
        String key = "test:" + UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(4);
        try {
            Callable<Boolean> request = () -> new TransactionTemplate(transactions).execute(status ->
                new SharedRateWindow(jdbc).consume(key, 3, 120));
            int accepted = 0;
            for (var response : pool.invokeAll(Collections.nCopies(9, request))) if (response.get()) accepted++;
            assertEquals(3, accepted);
            jdbc.update("UPDATE security_rate_window SET expires_at=UTC_TIMESTAMP(6) WHERE key_hash=UNHEX(SHA2(?,256))", key);
            assertEquals(true, request.call());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM security_rate_window WHERE key_hash=UNHEX(SHA2(?,256))", key);
        }
    }

    @Test void authAliasesAndBookingResourcesShareTheirOperationBudget() {
        assertEquals(RateLimitingFilter.sensitiveOperation("POST", "/api/auth/password/request-otp"),
                     RateLimitingFilter.sensitiveOperation("POST", "/auth/password/request-otp"));
        for (var endpoint : java.util.List.of("/api/auth/login", "/api/auth/otp/send", "/api/auth/otp/verify",
                "/api/auth/firebase/google", "/api/auth/password/reset", "/api/bookings"))
            assertNotNull(RateLimitingFilter.sensitiveOperation("POST", endpoint));
        assertEquals("booking:create", RateLimitingFilter.sensitiveOperation("POST","/api/spas/2/appointment-requests"));
        assertEquals("booking:hold", RateLimitingFilter.sensitiveOperation("POST","/api/spas/5/services/2/slots/3/hold"));
    }
}
