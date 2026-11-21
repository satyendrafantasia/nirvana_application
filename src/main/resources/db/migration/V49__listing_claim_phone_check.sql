-- A one-time code sent by SMS to the phone number on a Google listing, proving the claimant
-- can answer that phone. Only a hash of the code is kept.
CREATE TABLE listing_claim_phone_check (
  id bigint NOT NULL AUTO_INCREMENT,
  verification_id varchar(64) NOT NULL,
  user_id bigint NOT NULL,
  google_place_id varchar(255) NOT NULL,
  phone_number varchar(32) NOT NULL,
  code_hash char(64) NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL,
  expires_at datetime(6) NOT NULL,
  verified_at datetime(6) DEFAULT NULL,
  consumed_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_listing_phone_check_verification (verification_id),
  KEY idx_listing_phone_check_user (user_id, created_at),
  KEY idx_listing_phone_check_phone (phone_number, created_at),
  CONSTRAINT fk_listing_phone_check_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

ALTER TABLE spa_listing_claim
  ADD COLUMN phone_verified_at datetime(6) DEFAULT NULL AFTER consented_at,
  ADD COLUMN phone_verification_id varchar(64) DEFAULT NULL AFTER phone_verified_at;
