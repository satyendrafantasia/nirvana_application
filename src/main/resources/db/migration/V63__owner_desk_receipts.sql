CREATE TABLE spa_desk_receipt (
    booking_id BIGINT PRIMARY KEY,
    spa_id BIGINT NOT NULL,
    collected_by BIGINT NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    method VARCHAR(12) NOT NULL,
    receipt_reference VARCHAR(80),
    FOREIGN KEY (booking_id) REFERENCES booking(id),
    FOREIGN KEY (spa_id) REFERENCES spa(id),
    FOREIGN KEY (collected_by) REFERENCES app_user(id),
    INDEX ix_desk_spa_collected (spa_id, collected_at)
);
