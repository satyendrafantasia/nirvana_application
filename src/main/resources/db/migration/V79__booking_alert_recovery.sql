ALTER TABLE booking ADD notification_revision INT NOT NULL DEFAULT 0;
UPDATE booking SET notification_revision=COALESCE(reschedule_count,0);
ALTER TABLE spa_booking_confirmation ADD delivery_revision INT NOT NULL DEFAULT 0;
UPDATE spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id
SET c.delivery_revision=b.notification_revision;
UPDATE spa_booking_confirmation SET status='NEEDS_CONTACT',responded_at=NULL WHERE status='AUTO_CONFIRMED';
UPDATE spa_booking_confirmation c JOIN booking_alert_job j ON j.booking_id=c.booking_id AND j.revision=c.delivery_revision
SET c.sent_at=NULL WHERE j.audience='SPA_MOBILE' AND j.state IN ('FAILED','NEEDS_CONTACT') AND c.status IN ('PENDING','NEEDS_CONTACT');

ALTER TABLE appointment_request
    ADD attempt_no INT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY request_attempt(user_id,slot_id,attempt_no),
    DROP INDEX uq_request;
UPDATE appointment_request SET state='EXPIRED' WHERE state='PENDING' AND deadline<=UTC_TIMESTAMP();
ALTER TABLE booking_alert_job
    MODIFY booking_id BIGINT NULL,
    ADD request_id BIGINT NULL,
    ADD assigned_to BIGINT NULL,
    ADD UNIQUE KEY request_alert_once(request_id,event_kind,audience),
    ADD FOREIGN KEY(request_id) REFERENCES appointment_request(id);
CREATE TABLE booking_alert_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action_name VARCHAR(16) NOT NULL,
    note VARCHAR(500),
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(job_id) REFERENCES booking_alert_job(id)
);
INSERT INTO booking_alert_job(booking_id,event_kind,audience,revision,state)
SELECT c.booking_id,'FOLLOW_UP','SPA_MOBILE',c.delivery_revision,'NEEDS_CONTACT'
FROM spa_booking_confirmation c JOIN booking b ON b.id=c.booking_id
WHERE c.status='NEEDS_CONTACT' AND b.status='CONFIRMED' AND b.end_ts>UTC_TIMESTAMP();
