-- Couples and group bookings: one booker books 2 to 6 guests at the same start time.
-- Each guest keeps an ordinary booking row (its own service, therapist, room, receipt, review,
-- payout line, GST invoice and health form). A booking group links those rows and records the
-- single checkout they came from.

CREATE TABLE booking_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spa_id BIGINT NOT NULL,
    booker_user_id BIGINT NOT NULL,
    group_size TINYINT NOT NULL,
    start_ts DATETIME(6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_cents INT NOT NULL,
    payment_mode VARCHAR(16) NOT NULL,
    -- One Razorpay order and payment for the whole group. Each guest booking gets its own
    -- payment row for its share once the payment is captured.
    razorpay_order_id VARCHAR(128) NULL,
    razorpay_payment_id VARCHAR(128) NULL,
    order_amount_cents INT NULL,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    captured_at DATETIME(6) NULL,
    request_key VARCHAR(128) NULL,
    fingerprint CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_booking_group_size CHECK (group_size BETWEEN 2 AND 6),
    UNIQUE KEY uq_booking_group_order (razorpay_order_id),
    UNIQUE KEY uq_booking_group_payment (razorpay_payment_id),
    UNIQUE KEY uq_booking_group_request (booker_user_id, request_key),
    KEY idx_booking_group_spa_start (spa_id, start_ts),
    FOREIGN KEY (spa_id) REFERENCES spa(id) ON DELETE CASCADE,
    FOREIGN KEY (booker_user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- One row per guest. Position 1 is the booker. Guest name and phone are personal data.
CREATE TABLE booking_group_member (
    booking_id BIGINT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    position TINYINT NOT NULL,
    guest_name VARCHAR(120) NOT NULL,
    guest_phone VARCHAR(20) NULL,
    is_booker BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_booking_group_position (group_id, position),
    FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES booking_group(id) ON DELETE CASCADE
);

-- Holds made together for one group. They are created and converted all at once.
CREATE TABLE booking_group_hold (
    hold_id BIGINT PRIMARY KEY,
    group_token VARCHAR(64) NOT NULL,
    position TINYINT NOT NULL,
    service_id BIGINT NOT NULL,
    UNIQUE KEY uq_booking_group_hold_position (group_token, position),
    FOREIGN KEY (hold_id) REFERENCES booking_hold(id) ON DELETE CASCADE
);

-- "Couples room / side by side": guests of the same group taking this service can share one
-- room, up to this many at once (also capped by spa_room.capacity when that is set).
-- NULL means every guest needs a room of their own.
ALTER TABLE service ADD COLUMN side_by_side_capacity TINYINT NULL;

-- A group's guest bookings share one Razorpay payment. Their payment rows keep transaction_id
-- empty (it is unique) and name the shared provider payment here, so refunds of one guest's share
-- go to the right provider payment.
ALTER TABLE payment ADD COLUMN shared_gateway_payment_id VARCHAR(128) NULL;
CREATE INDEX idx_payment_shared_gateway ON payment (shared_gateway_payment_id);
