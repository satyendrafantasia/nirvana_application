CREATE TABLE therapist_daily_roster (
  therapist_id BIGINT NOT NULL,
  local_date DATE NOT NULL,
  intervals_json JSON NOT NULL,
  PRIMARY KEY (therapist_id, local_date),
  FOREIGN KEY (therapist_id) REFERENCES therapist(id) ON DELETE CASCADE
);
