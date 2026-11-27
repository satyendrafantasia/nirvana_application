CREATE TABLE booking_wallet_balance (
    user_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_cents BIGINT NOT NULL,
    PRIMARY KEY (user_id, currency),
    CONSTRAINT chk_wallet_balance CHECK (balance_cents >= 0)
);
INSERT INTO booking_wallet_balance (user_id,currency,balance_cents)
SELECT user_id,currency,SUM(amount_cents) FROM booking_wallet_credit GROUP BY user_id,currency;

CREATE TABLE booking_wallet_spend (
    booking_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_cents INT NOT NULL,
    returned_cents INT NULL,
    FOREIGN KEY (booking_id) REFERENCES booking(id),
    FOREIGN KEY (user_id,currency) REFERENCES booking_wallet_balance(user_id,currency),
    CONSTRAINT chk_wallet_spend CHECK (amount_cents > 0 AND (returned_cents IS NULL OR returned_cents BETWEEN 0 AND amount_cents))
);
