ALTER TABLE transactions ADD COLUMN payment_reference VARCHAR(32);
UPDATE transactions SET payment_reference = 'PAY-' || LPAD(CAST(id AS TEXT), 16, '0') WHERE payment_reference IS NULL;
ALTER TABLE transactions ALTER COLUMN payment_reference SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT uk_transactions_payment_reference UNIQUE (payment_reference);

ALTER TABLE transactions ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE transactions ADD COLUMN rail VARCHAR(32) NOT NULL DEFAULT 'ACCOUNT_TRANSFER';
ALTER TABLE transactions ADD COLUMN channel VARCHAR(32) NOT NULL DEFAULT 'WEB';
ALTER TABLE transactions ADD COLUMN device_id VARCHAR(255);
ALTER TABLE transactions ADD COLUMN payer_country VARCHAR(2);
ALTER TABLE transactions ADD COLUMN beneficiary_country VARCHAR(2);
CREATE INDEX idx_transactions_payment_reference ON transactions(payment_reference);
