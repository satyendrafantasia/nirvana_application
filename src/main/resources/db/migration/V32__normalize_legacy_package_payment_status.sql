-- The original demo migration used PAID, which is not a PaymentStatus value.
UPDATE user_package_subscription SET payment_status = 'COMPLETED' WHERE payment_status = 'PAID';
