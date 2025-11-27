-- Make therapist type selection universally available across spas
UPDATE `spa`
SET `allow_therapist_type_selection` = b'1'
WHERE `allow_therapist_type_selection` IS NULL OR `allow_therapist_type_selection` = b'0';

ALTER TABLE `spa`
    MODIFY `allow_therapist_type_selection` bit(1) NOT NULL DEFAULT b'1';
