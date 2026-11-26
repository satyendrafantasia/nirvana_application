-- Loyalty points ledger, referrals and gift cards.
-- All timestamps are UTC. Amounts are in minor currency units (paise).

-- Every change to a customer's points is one row here; app_user.loyalty_points is the running
-- balance kept in step with it. Credit rows are also "lots": remaining counts the points of that
-- credit not yet spent or expired, and redemptions use the lots that expire first.
CREATE TABLE loyalty_ledger (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  booking_id BIGINT NULL,
  entry_type VARCHAR(24) NOT NULL,
  points INT NOT NULL,
  balance_after INT NOT NULL,
  remaining INT NOT NULL DEFAULT 0,
  expires_at DATETIME(6) NULL,
  ref_key VARCHAR(100) NULL,
  note VARCHAR(255) NULL,
  actor_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_loyalty_ref (ref_key),
  INDEX ix_loyalty_user (user_id, id),
  INDEX ix_loyalty_lots (user_id, remaining, expires_at),
  INDEX ix_loyalty_expiry (remaining, expires_at),
  INDEX ix_loyalty_booking (booking_id),
  CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_loyalty_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE SET NULL
);

-- Points used to be worth one paisa each; they are now worth one rupee each (configurable).
-- Carry existing balances over at the same rupee value as one opening lot that expires in 12 months.
INSERT INTO loyalty_ledger (user_id, entry_type, points, balance_after, remaining, expires_at, ref_key, note, created_at)
SELECT id, 'MIGRATION', FLOOR(loyalty_points / 100), FLOOR(loyalty_points / 100), FLOOR(loyalty_points / 100),
       UTC_TIMESTAMP(6) + INTERVAL 12 MONTH, CONCAT('migration:', id), 'Opening balance carried over', UTC_TIMESTAMP(6)
FROM app_user WHERE loyalty_points >= 100;
UPDATE app_user SET loyalty_points = FLOOR(loyalty_points / 100) WHERE loyalty_points <> 0;
UPDATE booking SET loyalty_points_redeemed = ROUND(loyalty_points_redeemed / 100) WHERE loyalty_points_redeemed <> 0;
UPDATE booking SET loyalty_points_earned = FLOOR(loyalty_points_earned / 100) WHERE loyalty_points_earned <> 0;

-- One row per referred customer. rewarded_phone is unique, so a phone number can earn a
-- referral reward only once however many accounts are opened with it.
CREATE TABLE referral (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  referrer_id BIGINT NOT NULL,
  referee_id BIGINT NOT NULL,
  code VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(160) NULL,
  rewarded_phone VARCHAR(32) NULL,
  booking_id BIGINT NULL,
  reward_type VARCHAR(12) NULL,
  referrer_points INT NOT NULL DEFAULT 0,
  referee_points INT NOT NULL DEFAULT 0,
  referrer_coupon VARCHAR(64) NULL,
  referee_coupon VARCHAR(64) NULL,
  rewarded_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_referral_referee (referee_id),
  UNIQUE KEY uq_referral_phone (rewarded_phone),
  INDEX ix_referral_referrer (referrer_id, rewarded_at),
  CONSTRAINT fk_referral_referrer FOREIGN KEY (referrer_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_referral_referee FOREIGN KEY (referee_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_referral_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE SET NULL
);

-- A gift card is a prepaid balance with a code. spa_id restricts it to one spa; NULL means any spa.
CREATE TABLE gift_card (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL,
  source VARCHAR(12) NOT NULL,
  purchaser_id BIGINT NULL,
  recipient_name VARCHAR(100) NULL,
  recipient_email VARCHAR(255) NOT NULL,
  message VARCHAR(300) NULL,
  spa_id BIGINT NULL,
  initial_cents INT NOT NULL,
  balance_cents INT NOT NULL,
  currency VARCHAR(8) NOT NULL,
  razorpay_order_id VARCHAR(64) NULL,
  payment_reference_id VARCHAR(64) NULL,
  validity_days INT NOT NULL,
  expires_at DATETIME(6) NULL,
  activated_at DATETIME(6) NULL,
  email_sent_at DATETIME(6) NULL,
  voided_at DATETIME(6) NULL,
  void_reason VARCHAR(300) NULL,
  issued_by BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_gift_code (code),
  UNIQUE KEY uq_gift_order (razorpay_order_id),
  INDEX ix_gift_purchaser (purchaser_id, created_at),
  INDEX ix_gift_spa (spa_id, status),
  INDEX ix_gift_expiry (status, expires_at),
  CONSTRAINT fk_gift_purchaser FOREIGN KEY (purchaser_id) REFERENCES app_user(id) ON DELETE SET NULL,
  CONSTRAINT fk_gift_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);

-- Every change to a gift card balance. Redemptions carry the booking and the spa where they were spent.
CREATE TABLE gift_card_txn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  gift_card_id BIGINT NOT NULL,
  booking_id BIGINT NULL,
  spa_id BIGINT NULL,
  txn_type VARCHAR(16) NOT NULL,
  amount_cents INT NOT NULL,
  balance_after INT NOT NULL,
  ref_key VARCHAR(100) NULL,
  actor_id BIGINT NULL,
  note VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_gift_txn_ref (ref_key),
  INDEX ix_gift_txn_card (gift_card_id, id),
  INDEX ix_gift_txn_spa (spa_id, created_at),
  INDEX ix_gift_txn_booking (booking_id),
  CONSTRAINT fk_gift_txn_card FOREIGN KEY (gift_card_id) REFERENCES gift_card(id),
  CONSTRAINT fk_gift_txn_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE SET NULL
);

-- The part of a booking's total paid from a gift card (after tax). remainder_cents is what is left to pay.
ALTER TABLE booking ADD gift_card_cents INT NOT NULL DEFAULT 0;
