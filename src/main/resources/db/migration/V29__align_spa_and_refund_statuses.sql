-- These Java states were added without updating the original native MySQL enums.
-- Preserve all existing states and data; do not automatically approve legacy spas.
ALTER TABLE spa MODIFY COLUMN kyc_status ENUM('PENDING','REJECTED','VERIFIED','APPROVED') NOT NULL;
ALTER TABLE booking MODIFY COLUMN refund_status ENUM('NONE','REQUESTED','APPROVED','REJECTED','PROCESSING','COMPLETED') NOT NULL;
