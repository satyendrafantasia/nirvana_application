ALTER TABLE therapist ADD public_name VARCHAR(128) NULL;
ALTER TABLE booking ADD therapist_preference VARCHAR(8) NULL;
ALTER TABLE booking_hold ADD therapist_preference VARCHAR(8) NULL, ADD requested_label VARCHAR(128) NULL;
