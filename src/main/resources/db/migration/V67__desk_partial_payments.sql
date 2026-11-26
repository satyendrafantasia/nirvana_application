-- A desk balance can now be taken in parts (an advance, then the rest), each with an optional tip.
-- Each part is its own receipt, so the booking is no longer the key. request_key makes a retried
-- submission land once.
ALTER TABLE spa_desk_receipt
    ADD INDEX ix_desk_booking (booking_id),
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD COLUMN tip_cents INT NOT NULL DEFAULT 0,
    ADD COLUMN request_key VARCHAR(64),
    ADD UNIQUE KEY uk_desk_request (spa_id, request_key);
