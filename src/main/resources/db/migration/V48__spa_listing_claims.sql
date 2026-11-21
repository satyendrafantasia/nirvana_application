-- An owner's claim of a Google Maps listing. The spa row holds what the owner submitted;
-- this row records who agreed, when, to which terms, and for which Place ID.
CREATE TABLE spa_listing_claim (
  id bigint NOT NULL AUTO_INCREMENT,
  spa_id bigint NOT NULL,
  user_id bigint NOT NULL,
  google_place_id varchar(255) NOT NULL,
  terms_version varchar(32) NOT NULL,
  declaration varchar(500) NOT NULL,
  consented_at datetime(6) NOT NULL,
  client_ip varchar(64) DEFAULT NULL,
  user_agent varchar(255) DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDING',
  reviewed_at datetime(6) DEFAULT NULL,
  reviewed_by bigint DEFAULT NULL,
  review_note varchar(1000) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_listing_claim_status (status, consented_at),
  KEY idx_listing_claim_place (google_place_id),
  KEY idx_listing_claim_user (user_id),
  CONSTRAINT fk_listing_claim_spa FOREIGN KEY (spa_id) REFERENCES spa (id),
  CONSTRAINT fk_listing_claim_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
