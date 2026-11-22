CREATE TABLE booking_wallet_credit (
 booking_id BIGINT PRIMARY KEY REFERENCES booking(id),
 user_id BIGINT NOT NULL, currency VARCHAR(3) NOT NULL, amount_cents INT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX wallet_owner (user_id, currency)
);
-- Recover customer cancellation credits recorded by the former log-only wallet handler.
INSERT INTO booking_wallet_credit (booking_id,user_id,currency,amount_cents)
SELECT b.id,b.user_id,p.currency,SUM(p.refunded_cents)
FROM booking b JOIN payment p ON p.booking_id=b.id
WHERE b.refund_route='WALLET_CREDIT' AND b.refund_status='COMPLETED'
 AND b.status='CANCELLED' AND p.payment_status='REFUNDED' AND p.refunded_cents>0
GROUP BY b.id,b.user_id,p.currency;

CREATE TABLE booking_alert_job (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 booking_id BIGINT NOT NULL REFERENCES booking(id),
 event_kind VARCHAR(20) NOT NULL, audience VARCHAR(20) NOT NULL,
 revision INT NOT NULL, attempts INT NOT NULL DEFAULT 0,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 next_attempt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY alert_once (booking_id,event_kind,audience,revision),
 INDEX alert_due (state,next_attempt)
);
