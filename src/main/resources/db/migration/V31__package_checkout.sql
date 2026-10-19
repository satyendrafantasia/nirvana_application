ALTER TABLE user_package_subscription
 ADD COLUMN razorpay_order_id VARCHAR(128) NULL,
 ADD COLUMN amount_cents INT NULL,
 ADD COLUMN currency VARCHAR(3) NULL,
 ADD COLUMN purchased_sessions INT NULL,
 ADD COLUMN purchased_validity_days INT NULL,
 ADD UNIQUE KEY uk_package_razorpay_order (razorpay_order_id);
