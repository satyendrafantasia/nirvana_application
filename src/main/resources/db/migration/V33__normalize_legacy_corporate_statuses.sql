-- Normalize values introduced by the original V18 demo migration.
UPDATE corporate_deal
SET coupon_type = CASE WHEN total_sessions_per_employee > 1 THEN 'MULTI_SERVICE' ELSE 'SINGLE_SERVICE' END
WHERE coupon_type = 'SESSION';
UPDATE corporate_employee_coupon
SET coupon_type = CASE WHEN total_sessions > 1 THEN 'MULTI_SERVICE' ELSE 'SINGLE_SERVICE' END
WHERE coupon_type = 'SESSION';
UPDATE corporate_onboarding_upload SET status = 'PROCESSED' WHERE status = 'COMPLETED';
