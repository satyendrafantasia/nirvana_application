-- Add idempotency and receipt support to payments, webhook event log, and refund routing metadata
ALTER TABLE payment
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN receipt_url VARCHAR(1000);

ALTER TABLE payment
    ADD UNIQUE KEY uk_payment_idempotency (idempotency_key);

CREATE TABLE IF NOT EXISTS payment_webhook_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(255) NOT NULL,
    gateway VARCHAR(64) NOT NULL,
    payment_id VARCHAR(255),
    payload TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_webhook_event_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE booking
    ADD COLUMN refund_route VARCHAR(32);
