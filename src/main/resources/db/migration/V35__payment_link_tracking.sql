ALTER TABLE payment ADD COLUMN payment_link_id VARCHAR(128) NULL, ADD COLUMN payment_link_order_id VARCHAR(128) NULL, ADD UNIQUE KEY uk_payment_link (payment_link_id);
