package com.nirvana.application.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SharedRateWindow {
    private final JdbcTemplate jdbc;

    /** The upsert holds the row lock through the count read. Expiry uses the database clock on all nodes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(String key, int limit, int windowSeconds) {
        jdbc.update("""
                INSERT INTO security_rate_window(key_hash, attempts, expires_at)
                VALUES (UNHEX(SHA2(?,256)), 1, TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)))
                ON DUPLICATE KEY UPDATE
                  attempts = IF(expires_at <= UTC_TIMESTAMP(6), 1, LEAST(attempts+1, 2147483647)),
                  expires_at = IF(expires_at <= UTC_TIMESTAMP(6), VALUES(expires_at), expires_at)
                """, key, windowSeconds);
        Integer used = jdbc.queryForObject(
                "SELECT attempts FROM security_rate_window WHERE key_hash=UNHEX(SHA2(?,256))",
                Integer.class, key);
        return used != null && used <= limit;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void pruneExpired() {
        jdbc.update("DELETE FROM security_rate_window WHERE expires_at <= UTC_TIMESTAMP(6) ORDER BY expires_at LIMIT 5000");
    }
}
