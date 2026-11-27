CREATE TABLE security_rate_window (
    key_hash BINARY(32) NOT NULL PRIMARY KEY,
    attempts INT UNSIGNED NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX ix_security_rate_expiry (expires_at)
) ENGINE=InnoDB;
