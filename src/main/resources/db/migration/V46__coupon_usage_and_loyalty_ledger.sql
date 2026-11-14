-- Loyalty points spent on, and earned from, each booking. Recording them on the booking lets a
-- cancellation give the spent points back and stops a completed booking being rewarded twice.
ALTER TABLE booking
    ADD COLUMN loyalty_points_redeemed int NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_points_earned int NOT NULL DEFAULT 0;

-- Coupon usage limits are enforced by counting the bookings that used the coupon.
CREATE INDEX idx_booking_coupon_user ON booking (coupon_id, user_id, status);
