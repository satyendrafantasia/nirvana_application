-- Abandoned-checkout recovery: at most one reminder per checkout that was left unpaid.
-- The reminder link carries a signed token (recovery id + nonce + expiry, no personal data);
-- token_nonce lets a link be matched to its row and revoked.
CREATE TABLE checkout_recovery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    token_nonce VARCHAR(32) NOT NULL,
    token_expires_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    clicked_at DATETIME(6) NULL,
    click_count INT NOT NULL DEFAULT 0,
    converted_at DATETIME(6) NULL,
    converted_booking_id BIGINT NULL,
    recovered_cents INT NULL,
    CONSTRAINT uk_checkout_recovery_booking UNIQUE (booking_id),
    CONSTRAINT uk_checkout_recovery_nonce UNIQUE (token_nonce),
    INDEX ix_checkout_recovery_user_sent (user_id, sent_at),
    INDEX ix_checkout_recovery_spa_sent (spa_id, sent_at),
    INDEX ix_checkout_recovery_open (converted_at, sent_at),
    CONSTRAINT fk_checkout_recovery_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_checkout_recovery_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_checkout_recovery_spa FOREIGN KEY (spa_id) REFERENCES spa (id) ON DELETE CASCADE,
    CONSTRAINT fk_checkout_recovery_converted FOREIGN KEY (converted_booking_id) REFERENCES booking (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- A guest can turn off "you left something in your basket" reminders. This is separate from
-- marketing_opt_in: new accounts start with marketing off, and these reminders are about a
-- checkout the guest started themselves. NULL means reminders are on.
ALTER TABLE app_user ADD COLUMN checkout_reminders_opted_out_at DATETIME(6) NULL;
