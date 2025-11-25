-- Create refund tracking table
CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    booking_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    processed_by BIGINT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    reason VARCHAR(500) NULL,
    status ENUM('REQUESTED','PROCESSING','APPROVED','REJECTED','COMPLETED') NOT NULL,
    prefer_voucher BIT(1) NOT NULL DEFAULT b'0',
    gateway_refund_id VARCHAR(128) NULL,
    voucher_code VARCHAR(64) NULL,
    processed_at DATETIME(6) NULL,
    meta JSON NULL,
    PRIMARY KEY (id),
    KEY idx_payment_refund_booking (booking_id),
    KEY idx_payment_refund_status (status),
    CONSTRAINT fk_payment_refund_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_payment_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_payment_refund_requested_by FOREIGN KEY (requested_by) REFERENCES app_user (id),
    CONSTRAINT fk_payment_refund_processed_by FOREIGN KEY (processed_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Create voucher issuance table
CREATE TABLE IF NOT EXISTS voucher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    note VARCHAR(500) NULL,
    issued_by VARCHAR(128) NULL,
    expires_at DATETIME(6) NULL,
    redeemed_at DATETIME(6) NULL,
    meta JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_voucher_code (code),
    KEY idx_voucher_user (user_id),
    CONSTRAINT fk_voucher_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_voucher_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
