-- Guest health and consent forms. Answers are sensitive personal data: the application encrypts them
-- (AES-GCM) before they reach this table, keeps them only for the booking's spa, and a daily job deletes
-- them a configurable number of days after the visit.

-- A few extra questions a spa asks for one service (for example, "Any recent sunburn?").
CREATE TABLE service_health_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spa_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    position INT NOT NULL,
    prompt VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_health_question_service (service_id, position),
    FOREIGN KEY (spa_id) REFERENCES spa(id) ON DELETE CASCADE,
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE
);

-- The terms the guest consents to. Without a row the platform's default wording is shown.
CREATE TABLE spa_health_terms (
    spa_id BIGINT PRIMARY KEY,
    terms VARCHAR(2000) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (spa_id) REFERENCES spa(id) ON DELETE CASCADE
);

-- One form per booking. Only the ciphertext is stored; nothing about the answers is in clear text.
CREATE TABLE booking_health_form (
    booking_id BIGINT PRIMARY KEY,
    spa_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    key_id VARCHAR(16) NOT NULL,
    ciphertext BLOB NOT NULL,
    consented_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_health_form_user (user_id, updated_at),
    KEY idx_health_form_spa (spa_id),
    FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
    FOREIGN KEY (spa_id) REFERENCES spa(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
