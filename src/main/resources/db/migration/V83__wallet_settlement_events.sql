ALTER TABLE booking_wallet_spend
  ADD COLUMN spent_at DATETIME(6) NULL,
  ADD COLUMN returned_at DATETIME(6) NULL;

UPDATE booking_wallet_spend w JOIN booking b ON b.id=w.booking_id
SET w.spent_at=b.created_at,
    w.returned_at=CASE WHEN w.returned_cents IS NOT NULL THEN COALESCE(b.cancelled_at,b.updated_at,b.created_at) END;

ALTER TABLE booking_wallet_spend
  MODIFY spent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD INDEX idx_wallet_spent_at(spent_at),
  ADD INDEX idx_wallet_returned_at(returned_at);

-- Wallet credits from an original paid cancellation debit the originating spa once.
-- V66 and the payment ledger already cover most historical rows.
INSERT INTO booking_refund_posting(payment_id,cumulative_cents,amount_cents,recorded_at)
SELECT p.id,p.refunded_cents,p.refunded_cents-COALESCE(f.posted,0),COALESCE(p.refunded_at,w.created_at)
FROM booking_wallet_credit w
JOIN booking b ON b.id=w.booking_id AND b.refund_route='WALLET_CREDIT'
JOIN payment p ON p.booking_id=w.booking_id
LEFT JOIN (SELECT payment_id,MAX(cumulative_cents) AS posted FROM booking_refund_posting GROUP BY payment_id) f
  ON f.payment_id=p.id
WHERE p.refunded_cents=w.amount_cents AND p.refunded_cents>COALESCE(f.posted,0) AND p.captured_at IS NOT NULL;
