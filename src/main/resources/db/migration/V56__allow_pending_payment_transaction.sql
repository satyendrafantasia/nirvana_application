-- A gateway order exists before the customer pays. Capture supplies the transaction ID.
ALTER TABLE payment MODIFY COLUMN transaction_id VARCHAR(255) NULL;
