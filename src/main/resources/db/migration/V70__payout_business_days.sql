CREATE TABLE payout_business_day (
 spa_id BIGINT NOT NULL,currency VARCHAR(8) NOT NULL,covered_date DATE NOT NULL,statement_date DATE NOT NULL,
 PRIMARY KEY(spa_id,currency,covered_date)
);
