package com.nirvana.application.service.erasure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit trail for account deletion. Entries carry the account's internal number and an event
 * code only: never a name, email, phone or address, so the log itself holds no personal data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountErasureLog {

    public static final String REQUESTED = "REQUESTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String DEFERRED = "DEFERRED";
    public static final String ERASED = "ERASED";

    private final JdbcTemplate db;

    public void record(long userRef, String event, String detail) {
        db.update("INSERT INTO account_erasure_audit(user_ref, event, detail) VALUES (?,?,?)", userRef, event, detail);
        log.info("Account erasure {} for account {}{}", event, userRef, detail == null ? "" : " (" + detail + ")");
    }

    /**
     * Signing in during the grace period keeps the account. Called wherever a new session is
     * issued, so every sign-in method (password, code, Google, email link) cancels the request.
     *
     * @return true when a pending deletion was cancelled
     */
    @Transactional
    public boolean cancelOnSignIn(long userId) {
        int changed = db.update("UPDATE app_user SET data_erasure_requested_at = NULL "
                + "WHERE id = ? AND data_erasure_requested_at IS NOT NULL AND data_erased_at IS NULL", userId);
        if (changed > 0) record(userId, CANCELLED, "signed in during the grace period");
        return changed > 0;
    }
}
