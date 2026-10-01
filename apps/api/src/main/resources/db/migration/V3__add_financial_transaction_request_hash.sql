ALTER TABLE financial_transactions
    ADD COLUMN request_hash VARCHAR(64);

UPDATE financial_transactions
SET request_hash = repeat('0', 64)
WHERE request_hash IS NULL;

ALTER TABLE financial_transactions
    ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transaction_request_hash
    CHECK (request_hash ~ '^[0-9a-fA-F]{64}$');
