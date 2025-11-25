-- Hold and resumable onboarding drafts for offline-friendly UX
CREATE TABLE onboarding_draft (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    spa_id BIGINT NULL,
    step_key VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    resume_token VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_client_event_at DATETIME(6) NULL,
    client_request_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_onboarding_draft_token (resume_token)
);

CREATE INDEX idx_onboarding_draft_owner ON onboarding_draft(owner_user_id);
CREATE INDEX idx_onboarding_draft_expires ON onboarding_draft(expires_at);
