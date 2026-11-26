ALTER TABLE spa_desk_receipt
    ADD COLUMN returned_at DATETIME(6),
    ADD COLUMN returned_by BIGINT,
    ADD CONSTRAINT fk_desk_return_actor FOREIGN KEY (returned_by) REFERENCES app_user(id),
    ADD INDEX ix_desk_return_date (spa_id, returned_at);
