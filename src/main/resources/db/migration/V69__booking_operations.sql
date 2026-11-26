ALTER TABLE spa ADD booking_mode VARCHAR(12) NOT NULL DEFAULT 'INSTANT';
UPDATE spa SET booking_mode='ENQUIRY' WHERE JSON_EXTRACT(meta,'$.onboardedFromLead') IS NOT NULL;

CREATE TABLE booking_policy_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, spa_id BIGINT NOT NULL, mode VARCHAR(12) NOT NULL, actor BIGINT NOT NULL, updated_at DATETIME NOT NULL
);
CREATE TABLE checkout_attempt (
 spa_id BIGINT NOT NULL, user_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL,
 fingerprint CHAR(64) NOT NULL, booking_id BIGINT NOT NULL,
 PRIMARY KEY(spa_id,user_id,request_key), FOREIGN KEY(booking_id) REFERENCES booking(id)
);
CREATE TABLE appointment_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, spa_id BIGINT NOT NULL, slot_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 state VARCHAR(12) NOT NULL, deadline DATETIME(6) NOT NULL, booking_id BIGINT, answered_by BIGINT,
 UNIQUE KEY uq_request(user_id,slot_id), INDEX ix_requests(spa_id,state,deadline),
 FOREIGN KEY(slot_id) REFERENCES slot(id), FOREIGN KEY(booking_id) REFERENCES booking(id)
);
CREATE TABLE desk_guest (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, spa_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 guest_name VARCHAR(100) NOT NULL, contact VARCHAR(100), UNIQUE KEY uq_guest(spa_id,user_id)
);
CREATE TABLE desk_origin (
 booking_id BIGINT PRIMARY KEY, spa_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL, UNIQUE KEY uq_origin(spa_id,request_key), channel VARCHAR(12) NOT NULL, actor BIGINT NOT NULL, FOREIGN KEY(booking_id) REFERENCES booking(id)
);
CREATE TABLE spa_financial_report (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, spa_id BIGINT NOT NULL, business_date DATE NOT NULL, currency VARCHAR(8) NOT NULL,
 kind VARCHAR(12) NOT NULL, revision INT NOT NULL, state VARCHAR(16) NOT NULL, actor BIGINT NOT NULL,
 recorded_at DATETIME(6) NOT NULL, details JSON NOT NULL,
 UNIQUE KEY uq_report(spa_id,business_date,currency,kind,revision)
);
CREATE TABLE spa_transfer_reference (
 reference_no VARCHAR(100) PRIMARY KEY, spa_id BIGINT NOT NULL, business_date DATE NOT NULL, currency VARCHAR(8) NOT NULL
);
