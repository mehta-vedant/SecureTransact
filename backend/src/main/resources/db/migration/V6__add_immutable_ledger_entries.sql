CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    direction VARCHAR(16) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_ledger_entries_transaction_type ON ledger_entries(transaction_id, entry_type);
CREATE INDEX idx_ledger_entries_account_created ON ledger_entries(account_id, created_at);
