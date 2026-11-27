ALTER TABLE refresh_token
    ADD COLUMN mfa_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN session_family VARCHAR(36) NULL,
    ADD INDEX idx_refresh_live_family (user_id, session_family, revoked_at, expires_at);
