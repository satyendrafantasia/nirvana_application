ALTER TABLE `otp_verification`
    ADD COLUMN `purpose` VARCHAR(32) NOT NULL DEFAULT 'REGISTRATION' AFTER `verified`,
    ADD COLUMN `user_id` BIGINT DEFAULT NULL AFTER `purpose`,
    ADD COLUMN `login_token` VARCHAR(64) DEFAULT NULL AFTER `registration_token_expires_at`,
    ADD COLUMN `login_token_expires_at` DATETIME(6) DEFAULT NULL AFTER `login_token`,
    ADD COLUMN `login_consumed` BIT NOT NULL DEFAULT b'0' AFTER `login_token_expires_at`,
    ADD COLUMN `password_reset_token` VARCHAR(64) DEFAULT NULL AFTER `login_consumed`,
    ADD COLUMN `password_reset_token_expires_at` DATETIME(6) DEFAULT NULL AFTER `password_reset_token`,
    ADD COLUMN `password_reset_consumed` BIT NOT NULL DEFAULT b'0' AFTER `password_reset_token_expires_at`;

CREATE INDEX `idx_otp_verification_login_token` ON `otp_verification` (`login_token`);
CREATE INDEX `idx_otp_verification_password_reset_token` ON `otp_verification` (`password_reset_token`);
