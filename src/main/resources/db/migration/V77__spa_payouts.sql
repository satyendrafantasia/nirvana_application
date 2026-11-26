-- Spa payouts: what Nirvana owes each spa for online money it collected, and the transfer that pays it.

-- A payout is one statement: online money in for the period, less online refunds, less commission.
ALTER TABLE payout
  ADD COLUMN period_start DATETIME(6) NULL,
  ADD COLUMN period_end DATETIME(6) NULL,
  ADD COLUMN gross_cents BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN refunds_cents BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN commission_cents BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN commission_pct INT NOT NULL DEFAULT 0,
  ADD COLUMN provider VARCHAR(24) NULL,
  ADD COLUMN transfer_id VARCHAR(64) NULL,
  ADD COLUMN failure_reason VARCHAR(500) NULL,
  ADD COLUMN attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN last_attempt_at DATETIME(6) NULL,
  ADD COLUMN paid_at DATETIME(6) NULL,
  ADD UNIQUE KEY uk_payout_transfer (transfer_id),
  ADD KEY ix_payout_spa_period (spa_id, currency, period_end);

-- Every online payment, package sale and refund lands in exactly one payout: the unique key makes a
-- second settlement run (or two at once) unable to pay the same money twice.
CREATE TABLE payout_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payout_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  source_id BIGINT NOT NULL,
  source_seq INT NOT NULL DEFAULT 0,
  amount_cents BIGINT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  reference VARCHAR(120) NULL,
  UNIQUE KEY uk_payout_item_source (kind, source_id, source_seq),
  KEY ix_payout_item_payout (payout_id),
  CONSTRAINT fk_payout_item_payout FOREIGN KEY (payout_id) REFERENCES payout(id)
);

-- Where a spa is paid. Only the last four digits of the account number are kept; the full number goes
-- to Razorpay once, when the Route linked account is set up, and is never stored.
CREATE TABLE spa_payout_account (
  spa_id BIGINT NOT NULL PRIMARY KEY,
  account_holder VARCHAR(120) NOT NULL,
  ifsc VARCHAR(11) NOT NULL,
  account_last4 VARCHAR(4) NOT NULL,
  business_type VARCHAR(32) NOT NULL DEFAULT 'proprietorship',
  route_account_id VARCHAR(64) NULL,
  route_product_id VARCHAR(64) NULL,
  route_status VARCHAR(24) NOT NULL DEFAULT 'NOT_LINKED',
  route_error VARCHAR(500) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_payout_account_route (route_account_id),
  CONSTRAINT fk_payout_account_spa FOREIGN KEY (spa_id) REFERENCES spa(id)
);
