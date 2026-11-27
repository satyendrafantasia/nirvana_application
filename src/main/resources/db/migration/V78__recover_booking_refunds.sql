ALTER TABLE booking MODIFY refund_status ENUM('NONE','REQUESTED','APPROVED','REJECTED','PROCESSING','COMPLETED','FAILED') NOT NULL;
ALTER TABLE payment_refund
  MODIFY status ENUM('REQUESTED','PROCESSING','APPROVED','REJECTED','COMPLETED','FAILED','REVIEW_REQUIRED') NOT NULL,
  ADD COLUMN gateway_attempt INT NOT NULL DEFAULT 1,
  ADD COLUMN historical_posting BIT(1) NOT NULL DEFAULT 0,
  ADD COLUMN next_check_at DATETIME(6) NULL,
  ADD INDEX idx_refund_recovery (status,next_check_at,id);

-- Late captures used to set a booking flag without creating a support case.
INSERT INTO payment_refund (created_at,updated_at,booking_id,payment_id,requested_by,amount_cents,currency,reason,status,prefer_voucher)
SELECT UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),b.id,p.id,b.user_id,p.amount_cents-COALESCE(p.refunded_cents,0),p.currency,
       'Captured after booking closed or switched to pay at spa; verify the original payment.', 'REQUESTED',0
FROM booking b JOIN payment p ON p.booking_id=b.id
WHERE b.refund_status='REQUESTED' AND b.user_id IS NOT NULL
  AND p.gateway='RAZORPAY' AND p.transaction_id IS NOT NULL
  AND p.payment_status IN ('CAPTURED','COMPLETED','PARTIALLY_REFUNDED') AND p.amount_cents>COALESCE(p.refunded_cents,0)
  AND NOT EXISTS (SELECT 1 FROM payment_refund r WHERE r.payment_id=p.id);

-- Older releases posted refunds before the provider confirmed settlement. Never resubmit them on upgrade.
UPDATE payment_refund
SET status=IF(gateway_refund_id IS NULL,'REVIEW_REQUIRED','PROCESSING'), gateway_attempt=0,historical_posting=1
WHERE voucher_code IS NULL AND (status='PROCESSING' OR (status='COMPLETED' AND gateway_refund_id IS NOT NULL));

INSERT INTO payment_refund (created_at,updated_at,booking_id,payment_id,requested_by,amount_cents,currency,reason,status,prefer_voucher,gateway_attempt,historical_posting)
SELECT UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),b.id,p.id,b.user_id,p.refunded_cents,p.currency,
       'Historical original-payment refund. Verify the refund reference in Razorpay.', 'REVIEW_REQUIRED',0,0,1
FROM booking b JOIN payment p ON p.booking_id=b.id
WHERE b.status='CANCELLED' AND b.refund_status IN ('COMPLETED','PROCESSING') AND b.user_id IS NOT NULL
  AND COALESCE(b.refund_route,'ORIGINAL_METHOD')='ORIGINAL_METHOD' AND p.gateway='RAZORPAY'
  AND p.transaction_id IS NOT NULL AND p.refunded_cents>0
  AND NOT EXISTS (SELECT 1 FROM payment_refund r WHERE r.payment_id=p.id);

UPDATE booking b SET refund_status='PROCESSING'
WHERE EXISTS (SELECT 1 FROM payment_refund r WHERE r.booking_id=b.id AND r.gateway_attempt=0
              AND r.status IN ('PROCESSING','REVIEW_REQUIRED'));
