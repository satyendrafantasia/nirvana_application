-- Slot capacity and row locking control availability. A unique FK prevented a second
-- guest booking and even prevented rebooking a slot after cancellation.
ALTER TABLE booking DROP INDEX idx_booking_slot, ADD INDEX idx_booking_slot (slot_id);
