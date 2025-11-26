CREATE TABLE IF NOT EXISTS corporate (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    contact_email VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_corporate_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS corporate_deal (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    corporate_id BIGINT NOT NULL,
    deal_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    coupon_type VARCHAR(64) NOT NULL,
    total_sessions_per_employee INT,
    global_package_type VARCHAR(128),
    start_date date,
    end_date date,
    status VARCHAR(32) NOT NULL,
    corporate_payment_status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_corporate_deal_corporate FOREIGN KEY (corporate_id) REFERENCES corporate(id),
    INDEX idx_corporate_deal_corp (corporate_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS corporate_employee (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    corporate_id BIGINT NOT NULL,
    user_id BIGINT,
    employee_email VARCHAR(255) NOT NULL,
    employee_identifier VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_corporate_employee_corporate FOREIGN KEY (corporate_id) REFERENCES corporate(id),
    CONSTRAINT fk_corporate_employee_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_corporate_employee_email (employee_email, corporate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS corporate_employee_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    corporate_id BIGINT NOT NULL,
    corporate_deal_id BIGINT NOT NULL,
    corporate_employee_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    coupon_type VARCHAR(64) NOT NULL,
    total_sessions INT,
    remaining_sessions INT,
    global_package_type VARCHAR(128),
    start_date date,
    expiry_date date,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_employee_coupon_corporate FOREIGN KEY (corporate_id) REFERENCES corporate(id),
    CONSTRAINT fk_employee_coupon_deal FOREIGN KEY (corporate_deal_id) REFERENCES corporate_deal(id),
    CONSTRAINT fk_employee_coupon_employee FOREIGN KEY (corporate_employee_id) REFERENCES corporate_employee(id),
    CONSTRAINT fk_employee_coupon_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_employee_coupon_user (user_id, status, expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS corporate_coupon_usage_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    corporate_employee_coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    corporate_id BIGINT NOT NULL,
    booking_id BIGINT,
    spa_id BIGINT,
    usage_datetime datetime(6) NOT NULL,
    session_number INT,
    notes VARCHAR(1000),
    CONSTRAINT fk_corp_coupon_usage_coupon FOREIGN KEY (corporate_employee_coupon_id) REFERENCES corporate_employee_coupon(id),
    CONSTRAINT fk_corp_coupon_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_corp_coupon_usage_corporate FOREIGN KEY (corporate_id) REFERENCES corporate(id),
    INDEX idx_coupon_usage_coupon (corporate_employee_coupon_id, usage_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS corporate_onboarding_upload (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at datetime(6) NOT NULL,
    updated_at datetime(6) NOT NULL,
    corporate_id BIGINT NOT NULL,
    corporate_deal_id BIGINT NOT NULL,
    original_file_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    total_records INT,
    success_count INT,
    failure_count INT,
    CONSTRAINT fk_onboarding_corporate FOREIGN KEY (corporate_id) REFERENCES corporate(id),
    CONSTRAINT fk_onboarding_deal FOREIGN KEY (corporate_deal_id) REFERENCES corporate_deal(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS corporate_employee_coupon_id BIGINT NULL;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS payment_source_type VARCHAR(32) DEFAULT 'NORMAL';

SET @fk_booking_coupon := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'booking'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
      AND CONSTRAINT_NAME = 'fk_booking_corporate_coupon'
    LIMIT 1
);

SET @drop_fk_sql := IF(@fk_booking_coupon IS NOT NULL,
    'ALTER TABLE booking DROP FOREIGN KEY fk_booking_corporate_coupon',
    'SELECT 1');

PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_booking_coupon := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'booking'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
      AND CONSTRAINT_NAME = 'fk_booking_corporate_coupon'
    LIMIT 1
);

SET @add_fk_sql := IF(@fk_booking_coupon IS NULL,
    'ALTER TABLE booking ADD CONSTRAINT fk_booking_corporate_coupon FOREIGN KEY (corporate_employee_coupon_id) REFERENCES corporate_employee_coupon(id)',
    'SELECT 1');

PREPARE stmt FROM @add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
