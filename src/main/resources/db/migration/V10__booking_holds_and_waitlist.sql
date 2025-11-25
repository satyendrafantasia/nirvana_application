CREATE TABLE IF NOT EXISTS booking_hold (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    spa_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    services_json JSON NULL,
    hold_token VARCHAR(128) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    hold_units INTEGER NOT NULL DEFAULT 1,
    converted_to_booking BOOLEAN NOT NULL DEFAULT FALSE,
    meta JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_booking_hold PRIMARY KEY (id),
    CONSTRAINT uk_booking_hold_token UNIQUE (hold_token),
    CONSTRAINT fk_booking_hold_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_booking_hold_spa FOREIGN KEY (spa_id) REFERENCES spa(id),
    CONSTRAINT fk_booking_hold_slot FOREIGN KEY (slot_id) REFERENCES slot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP INDEX IF EXISTS idx_hold_user ON booking_hold;
CREATE INDEX idx_hold_user ON booking_hold (user_id, expires_at);
DROP INDEX IF EXISTS idx_hold_slot ON booking_hold;
CREATE INDEX idx_hold_slot ON booking_hold (slot_id, expires_at);

CREATE TABLE IF NOT EXISTS waitlist_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    spa_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    slot_id BIGINT NULL,
    guests INTEGER NOT NULL DEFAULT 1,
    contact_email VARCHAR(256),
    contact_phone VARCHAR(32),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    notified_at DATETIME(6),
    meta JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_waitlist_entry PRIMARY KEY (id),
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_waitlist_spa FOREIGN KEY (spa_id) REFERENCES spa(id),
    CONSTRAINT fk_waitlist_service FOREIGN KEY (service_id) REFERENCES service(id),
    CONSTRAINT fk_waitlist_slot FOREIGN KEY (slot_id) REFERENCES slot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP INDEX IF EXISTS idx_waitlist_slot ON waitlist_entry;
CREATE INDEX idx_waitlist_slot ON waitlist_entry (slot_id, active, created_at);
DROP INDEX IF EXISTS idx_waitlist_user ON waitlist_entry;
CREATE INDEX idx_waitlist_user ON waitlist_entry (user_id, active, created_at);
