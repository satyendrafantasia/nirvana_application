CREATE TABLE owner_push_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii NOT NULL UNIQUE,
    registration_token VARCHAR(2048) CHARACTER SET ascii NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
ALTER TABLE booking_alert_job
    ADD COLUMN device_id BIGINT NOT NULL DEFAULT 0,
    DROP INDEX alert_once,
    ADD UNIQUE KEY alert_once (booking_id,event_kind,audience,revision,device_id);
