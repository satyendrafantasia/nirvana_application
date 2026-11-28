ALTER TABLE spa_listing_claim
    ADD COLUMN enquiries_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN enquiry_verification_id VARCHAR(64) NULL;
