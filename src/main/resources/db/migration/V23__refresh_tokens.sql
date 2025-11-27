-- Session refresh token store with device binding and audit metadata
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,
    device_fingerprint VARCHAR(128) DEFAULT NULL,
    user_agent VARCHAR(1024) DEFAULT NULL,
    ip_address VARCHAR(64) DEFAULT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) DEFAULT NULL,
    replaced_by_token VARCHAR(512) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_refresh_token_token (token),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT FK_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
