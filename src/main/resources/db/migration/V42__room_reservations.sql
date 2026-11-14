ALTER TABLE booking ADD room_id BIGINT NULL, ADD FOREIGN KEY (room_id) REFERENCES spa_room(id);
ALTER TABLE booking_hold ADD room_id BIGINT NULL, ADD therapist_id BIGINT NULL,
 ADD FOREIGN KEY (room_id) REFERENCES spa_room(id), ADD FOREIGN KEY (therapist_id) REFERENCES therapist(id);
UPDATE booking b JOIN slot s ON b.slot_id=s.id SET b.room_id=s.room_id WHERE s.room_id IS NOT NULL;
CREATE TABLE therapist_week (therapist_id BIGINT PRIMARY KEY, shifts_json JSON NOT NULL,
 FOREIGN KEY (therapist_id) REFERENCES therapist(id));
CREATE TABLE generated_room_slot (room_id BIGINT NOT NULL, original_start DATETIME(6) NOT NULL,
 PRIMARY KEY(room_id,original_start), FOREIGN KEY(room_id) REFERENCES spa_room(id));
