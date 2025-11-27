-- Enable therapist type preference and persist requested types
ALTER TABLE `spa`
    ADD COLUMN `allow_therapist_type_selection` bit(1) NOT NULL DEFAULT b'0' AFTER `allow_therapist_selection`;

ALTER TABLE `booking`
    ADD COLUMN `therapist_type` varchar(128) DEFAULT NULL AFTER `therapist_id`;
