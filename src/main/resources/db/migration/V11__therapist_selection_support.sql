-- Add therapist selection configuration and booking linkage

-- Spa-level toggle for exposing therapist selection to guests
ALTER TABLE `spa`
    ADD COLUMN `allow_therapist_selection` bit(1) NOT NULL DEFAULT b'0' AFTER `is_featured`;

-- Optional therapist reference on bookings to capture guest selection and enforce conflicts
ALTER TABLE `booking`
    ADD COLUMN `therapist_id` bigint DEFAULT NULL AFTER `provider_assigned_id`,
    ADD KEY `idx_booking_therapist_time` (`therapist_id`, `start_ts`, `end_ts`),
    ADD CONSTRAINT `FK_booking_therapist` FOREIGN KEY (`therapist_id`) REFERENCES `therapist` (`id`);
