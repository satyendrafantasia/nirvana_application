-- Each booking's confirmation by the spa: the spa gets an SMS with a private link to confirm or
-- decline. One last reminder follows; a spa that still hasn't replied has the booking confirmed for it.
CREATE TABLE spa_booking_confirmation (
    booking_id      BIGINT       NOT NULL,
    spa_id          BIGINT       NOT NULL,
    -- The private link's token: whoever has the SMS can answer for the spa, until the appointment is over.
    token           VARCHAR(32)  NOT NULL,
    -- PENDING, CONFIRMED, DECLINED, AUTO_CONFIRMED, NO_MOBILE (no number that takes SMS) or CLOSED (cancelled first).
    status          VARCHAR(20)  NOT NULL,
    phone           VARCHAR(20)  NULL,
    sent_at         DATETIME(6)  NULL,
    reminder_due_at DATETIME(6)  NULL,
    reminder_sent_at DATETIME(6) NULL,
    deadline_at     DATETIME(6)  NULL,
    responded_at    DATETIME(6)  NULL,
    decline_reason  VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (booking_id),
    UNIQUE KEY uk_spa_booking_confirmation_token (token),
    KEY idx_spa_booking_confirmation_due (status, reminder_due_at, deadline_at),
    CONSTRAINT fk_spa_booking_confirmation_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_spa_booking_confirmation_spa FOREIGN KEY (spa_id) REFERENCES spa (id)
);
