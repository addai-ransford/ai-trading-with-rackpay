ALTER TABLE financial_transactions
    ADD COLUMN wallet_id UUID,
    ADD COLUMN operation_type VARCHAR(20),
    ADD COLUMN amount NUMERIC(38, 18),
    ADD COLUMN currency_code VARCHAR(3),
    ADD COLUMN ledger_transaction_id UUID;

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transaction_wallet
    FOREIGN KEY (wallet_id) REFERENCES wallets(id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transaction_ledger
    FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions(id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transaction_operation_type
    CHECK (operation_type IS NULL OR operation_type IN ('CREDIT', 'DEBIT'));

ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transaction_amount
    CHECK (amount IS NULL OR amount > 0);

ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_financial_transaction_currency
    CHECK (currency_code IS NULL OR currency_code IN ('EUR', 'USD', 'GBP', 'GHS', 'KES', 'NGN', 'XOF', 'UGX'));

CREATE INDEX idx_financial_transactions_wallet_created
    ON financial_transactions(wallet_id, created_at DESC);

CREATE INDEX idx_financial_transactions_ledger
    ON financial_transactions(ledger_transaction_id);
