ALTER TABLE spa_listing_claim
    ADD COLUMN existing_inventory BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN submitted_profile JSON NULL,
    ADD COLUMN pending_inventory_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN existing_inventory = 1 AND status = 'PENDING' THEN spa_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_pending_inventory_claim (pending_inventory_id);
