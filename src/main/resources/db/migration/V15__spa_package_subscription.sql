-- Package subscription system schema

CREATE TABLE packages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    package_type VARCHAR(32) NOT NULL,
    price_cents INT NOT NULL,
    session_count INT NOT NULL,
    description VARCHAR(512) NULL,
    validity_days INT NULL,
    is_active BIT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_packages_type (package_type)
);

CREATE TABLE spa_packages (
    package_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    PRIMARY KEY (package_id, spa_id),
    CONSTRAINT fk_spa_packages_package FOREIGN KEY (package_id) REFERENCES packages(id),
    CONSTRAINT fk_spa_packages_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);

CREATE TABLE user_package_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    user_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    package_type VARCHAR(32) NOT NULL,
    purchase_date DATETIME(6) NOT NULL,
    expiry_date DATETIME(6) NULL,
    remaining_sessions INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    payment_reference VARCHAR(128) NULL,
    activated_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_subscription_user_status (user_id, status),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_subscription_package FOREIGN KEY (package_id) REFERENCES packages(id)
);

CREATE TABLE user_package_usage_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    subscription_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    session_number INT NOT NULL,
    used_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_usage_user (user_id, used_at),
    KEY idx_usage_subscription (subscription_id),
    CONSTRAINT fk_usage_subscription FOREIGN KEY (subscription_id) REFERENCES user_package_subscription(id),
    CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_usage_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
    CONSTRAINT fk_usage_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);

ALTER TABLE booking
    ADD COLUMN package_subscription_id BIGINT NULL,
    ADD COLUMN payment_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE booking
    ADD CONSTRAINT fk_booking_package_subscription FOREIGN KEY (package_subscription_id) REFERENCES user_package_subscription(id);
