-- Records that the appointment reminder for a booking has been handled, so it is sent at most
-- once and survives restarts. Cleared when the booking moves to a new time.
ALTER TABLE booking ADD COLUMN reminder_processed_at datetime(6) DEFAULT NULL;

-- Background jobs (reminders, unpaid-booking expiry, auto-completion) look bookings up by
-- status and start time across all spas.
CREATE INDEX idx_booking_status_start ON booking (status, start_ts);
