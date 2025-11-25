-- Add compliance and localization support

ALTER TABLE app_user
    ADD COLUMN privacy_consent_version VARCHAR(64) NULL,
    ADD COLUMN privacy_consented_at DATETIME(6) NULL,
    ADD COLUMN consent_source VARCHAR(128) NULL,
    ADD COLUMN data_erasure_requested_at DATETIME(6) NULL,
    ADD COLUMN data_erased_at DATETIME(6) NULL;

ALTER TABLE content_variant
    ADD COLUMN locale VARCHAR(16) NOT NULL DEFAULT 'en';
