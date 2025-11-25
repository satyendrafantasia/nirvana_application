-- Reviews for completed bookings
CREATE TABLE IF NOT EXISTS review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    spa_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL,
    title VARCHAR(255) NULL,
    text VARCHAR(4000) NULL,
    is_visible BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reply_text VARCHAR(2000) NULL,
    reply_by_provider_id BIGINT NULL,
    reply_at DATETIME(6) NULL,
    helpful_count INT NOT NULL DEFAULT 0,
    reported_count INT NOT NULL DEFAULT 0,
    meta JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_booking (booking_id),
    KEY idx_review_spa (spa_id, created_at DESC),
    KEY idx_review_user (user_id, created_at DESC),
    CONSTRAINT fk_review_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_review_spa FOREIGN KEY (spa_id) REFERENCES spa (id),
    CONSTRAINT fk_review_reply_provider FOREIGN KEY (reply_by_provider_id) REFERENCES provider_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Frequently Asked Questions
CREATE TABLE IF NOT EXISTS faq (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question VARCHAR(500) NOT NULL,
    answer VARCHAR(4000) NOT NULL,
    category VARCHAR(128) NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_faq_category (category, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A/B-tested content variants
CREATE TABLE IF NOT EXISTS content_variant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    experiment_key VARCHAR(128) NOT NULL,
    variant_key VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_variant_experiment (experiment_key, variant_key),
    KEY idx_content_variant_active (experiment_key, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
