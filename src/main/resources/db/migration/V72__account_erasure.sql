-- Account deletion under India's DPDP Act 2023.
-- app_user.data_erasure_requested_at marks a pending deletion (cancelled by signing in);
-- app_user.data_erased_at marks a completed one. The audit table records what happened
-- with the account's internal number only, never a name, email, phone or address.

CREATE TABLE account_erasure_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_ref BIGINT NOT NULL,
    event VARCHAR(32) NOT NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_erasure_audit_user (user_ref, created_at)
) ENGINE=InnoDB;

-- The deletion job looks for requests whose grace period has ended.
CREATE INDEX idx_appuser_erasure_due ON app_user (data_erased_at, data_erasure_requested_at);
