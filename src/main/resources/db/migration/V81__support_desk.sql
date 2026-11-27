ALTER TABLE message_thread MODIFY user_id BIGINT NULL,
  ADD assigned_agent_id BIGINT NULL,
  ADD guest_key_hash CHAR(64) NULL,
  ADD customer_unread BOOLEAN NOT NULL DEFAULT FALSE,
  ADD UNIQUE KEY uq_support_guest_key (guest_key_hash),
  ADD INDEX ix_support_queue (status, updated_at, id),
  ADD CONSTRAINT fk_support_assignee FOREIGN KEY (assigned_agent_id) REFERENCES app_user(id);
