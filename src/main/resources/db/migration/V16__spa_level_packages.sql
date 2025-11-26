
-- Guard: if a legacy spa_packages table exists without an id column, rename it so
-- the new catalog can be created with the correct primary key layout.
SET @legacy_spa_packages := (
    SELECT COUNT(*)
    FROM information_schema.tables t
    WHERE t.table_schema = DATABASE()
      AND t.table_name = 'spa_packages'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.columns c
          WHERE c.table_schema = DATABASE()
            AND c.table_name = 'spa_packages'
            AND c.column_name = 'id'
      )
);
SET @legacy_rename_sql := (
    SELECT IF(@legacy_spa_packages > 0,
              'RENAME TABLE spa_packages TO spa_packages_legacy_without_id;',
              'SELECT 1')
);
PREPARE stmt FROM @legacy_rename_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dedicated package catalog per spa
CREATE TABLE IF NOT EXISTS spa_packages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    spa_id BIGINT NOT NULL,
    level VARCHAR(32) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    free_sessions_count INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_spa_package_level (spa_id, level),
    KEY idx_spa_package_spa (spa_id),
    KEY idx_spa_package_status (status),
    CONSTRAINT fk_spa_package_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);

-- User purchases scoped to a single spa
CREATE TABLE IF NOT EXISTS user_spa_package_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    spa_package_id BIGINT NOT NULL,
    level VARCHAR(32) NOT NULL,
    price_paid DECIMAL(19,2) NOT NULL,
    total_sessions INT NOT NULL,
    remaining_sessions INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    purchase_date DATETIME(6) NOT NULL,
    expiry_date DATETIME(6) NULL,
    payment_status VARCHAR(32) NOT NULL,
    payment_reference_id VARCHAR(128) NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_user_spa_package_status (user_id, status, spa_id),
    KEY idx_user_spa_package_payment (payment_status),
    CONSTRAINT fk_user_spa_package_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_spa_package_spa FOREIGN KEY (spa_id) REFERENCES spa(id),
    CONSTRAINT fk_user_spa_package_catalog FOREIGN KEY (spa_package_id) REFERENCES spa_packages(id)
);

-- Usage audit per subscription
CREATE TABLE IF NOT EXISTS user_spa_package_usage_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_spa_package_subscription_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    usage_date DATETIME(6) NOT NULL,
    session_number INT NOT NULL,
    notes VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_user_spa_usage (user_id, spa_id),
    KEY idx_spa_subscription_usage (user_spa_package_subscription_id),
    CONSTRAINT fk_user_spa_pkg_usage_subscription FOREIGN KEY (user_spa_package_subscription_id) REFERENCES user_spa_package_subscription(id),
    CONSTRAINT fk_user_spa_pkg_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_spa_pkg_usage_spa FOREIGN KEY (spa_id) REFERENCES spa(id),
    CONSTRAINT fk_user_spa_pkg_usage_booking FOREIGN KEY (booking_id) REFERENCES booking(id)
);

-- Notification sink for spa owners/managers
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_user_id, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user(id)
);
