CREATE TABLE customer_google_identity (
    user_id BIGINT PRIMARY KEY,
    project_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    firebase_uid VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    google_email VARCHAR(255) NOT NULL,
    UNIQUE (project_id, firebase_uid),
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
