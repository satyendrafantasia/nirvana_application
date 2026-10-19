ALTER TABLE user_spa_package_subscription ADD COLUMN razorpay_order_id VARCHAR(128) NULL, ADD COLUMN currency VARCHAR(3) NULL, ADD UNIQUE KEY uk_spa_package_order (razorpay_order_id);
