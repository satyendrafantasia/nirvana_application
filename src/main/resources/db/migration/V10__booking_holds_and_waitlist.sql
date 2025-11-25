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

-- Do not drop idx_hold_user because it backs the foreign key. Instead, create a
-- dedicated composite index for the expires_at lookup when missing.
SET @booking_hold_idx_user_expires_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'booking_hold'
      AND index_name = 'idx_hold_user_expires'
);
SET @booking_hold_create_idx_user_expires_sql = IF(
    @booking_hold_idx_user_expires_exists = 0,
    'CREATE INDEX idx_hold_user_expires ON booking_hold (user_id, expires_at)',
    'SELECT 1'
);
PREPARE stmt FROM @booking_hold_create_idx_user_expires_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @booking_hold_idx_slot_expires_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'booking_hold'
      AND index_name = 'idx_hold_slot_expires'
);
SET @booking_hold_create_idx_slot_expires_sql = IF(
    @booking_hold_idx_slot_expires_exists = 0,
    'CREATE INDEX idx_hold_slot_expires ON booking_hold (slot_id, expires_at)',
    'SELECT 1'
);
PREPARE stmt FROM @booking_hold_create_idx_slot_expires_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @waitlist_entry_idx_slot_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'waitlist_entry'
      AND index_name = 'idx_waitlist_slot'
);
SET @waitlist_entry_create_idx_slot_sql = IF(
    @waitlist_entry_idx_slot_exists = 0,
    'CREATE INDEX idx_waitlist_slot ON waitlist_entry (slot_id, active, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @waitlist_entry_create_idx_slot_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @waitlist_entry_idx_user_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'waitlist_entry'
      AND index_name = 'idx_waitlist_user'
);
SET @waitlist_entry_create_idx_user_sql = IF(
    @waitlist_entry_idx_user_exists = 0,
    'CREATE INDEX idx_waitlist_user ON waitlist_entry (user_id, active, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @waitlist_entry_create_idx_user_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
