CREATE TABLE booking_refund_posting (
  payment_id BIGINT NOT NULL, cumulative_cents INT NOT NULL, amount_cents INT NOT NULL, recorded_at DATETIME(6) NOT NULL,
  PRIMARY KEY(payment_id,cumulative_cents), FOREIGN KEY(payment_id) REFERENCES payment(id),
  INDEX ix_booking_refund_date(recorded_at)
);
INSERT INTO booking_refund_posting(payment_id,cumulative_cents,amount_cents,recorded_at)
SELECT id,refunded_cents,refunded_cents,refunded_at FROM payment WHERE refunded_cents>0 AND refunded_at IS NOT NULL;

ALTER TABLE user_spa_package_subscription ADD COLUMN collected_at DATETIME(6);
UPDATE user_spa_package_subscription SET collected_at=purchase_date WHERE payment_status='SUCCESS';
CREATE TABLE spa_package_refund_posting (
  subscription_id BIGINT NOT NULL, cumulative_cents INT NOT NULL, amount_cents INT NOT NULL, recorded_at DATETIME(6) NOT NULL,
  PRIMARY KEY(subscription_id,cumulative_cents), FOREIGN KEY(subscription_id) REFERENCES user_spa_package_subscription(id),
  INDEX ix_spa_package_refund_date(recorded_at)
);
CREATE INDEX ix_owner_booking_date ON booking(spa_id,start_ts);
CREATE INDEX ix_owner_slot_date ON slot(spa_id,start_ts);
CREATE INDEX ix_owner_package_collection ON user_spa_package_subscription(spa_id,collected_at);
CREATE INDEX ix_owner_package_redemption ON user_spa_package_usage_log(spa_id,usage_date);
