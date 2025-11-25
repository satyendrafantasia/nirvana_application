-- Add payment_mode column for Booking

ALTER TABLE booking
    ADD COLUMN payment_mode VARCHAR(16) NOT NULL DEFAULT 'OFFLINE';

-- If you want ONLINE as default instead, change above default accordingly.
-- Optional: if you want to drop default later, you can run another migration.
