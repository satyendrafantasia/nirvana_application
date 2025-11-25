-- Notification templating
CREATE TABLE IF NOT EXISTS notification_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    code VARCHAR(128) NOT NULL,
    channel ENUM('EMAIL','SMS','WHATSAPP','PUSH') NOT NULL,
    name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NULL,
    body TEXT NULL,
    locale VARCHAR(16) NULL,
    description VARCHAR(500) NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    archived_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_template_code_channel (code, channel),
    KEY idx_notification_template_active (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notification_log
    ADD COLUMN title VARCHAR(255) NULL,
    ADD COLUMN message TEXT NULL,
    ADD COLUMN read_at DATETIME(6) NULL;

-- Messaging threads
CREATE TABLE IF NOT EXISTS message_thread (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    subject VARCHAR(255) NULL,
    status ENUM('OPEN','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    last_message_at DATETIME(6) NULL,
    unread_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_message_thread_user (user_id, updated_at),
    KEY idx_message_thread_status (status),
    CONSTRAINT fk_message_thread_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_message_thread_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Chat messages
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    thread_id BIGINT NOT NULL,
    sender_user_id BIGINT NULL,
    sender_type ENUM('USER','AGENT','SYSTEM') NOT NULL,
    content TEXT NULL,
    moderation_status ENUM('PENDING','APPROVED','REJECTED','FLAGGED') NOT NULL DEFAULT 'PENDING',
    moderation_note VARCHAR(500) NULL,
    moderated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_chat_message_thread (thread_id, created_at),
    KEY idx_chat_message_moderation (moderation_status),
    CONSTRAINT fk_chat_message_thread FOREIGN KEY (thread_id) REFERENCES message_thread (id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Chat attachments
CREATE TABLE IF NOT EXISTS message_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    message_id BIGINT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(255) NULL,
    size_bytes BIGINT NULL,
    moderation_status ENUM('PENDING','APPROVED','REJECTED','FLAGGED') NOT NULL DEFAULT 'PENDING',
    moderation_note VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_message_attachment_message (message_id),
    KEY idx_message_attachment_status (moderation_status),
    CONSTRAINT fk_message_attachment_message FOREIGN KEY (message_id) REFERENCES chat_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
