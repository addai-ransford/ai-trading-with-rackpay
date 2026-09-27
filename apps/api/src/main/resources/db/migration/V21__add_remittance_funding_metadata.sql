ALTER TABLE remittances
    ADD COLUMN request_hash VARCHAR(64),
    ADD COLUMN funding_financial_transaction_id UUID,
    ADD COLUMN clearing_account_id UUID;

ALTER TABLE remittances
    ADD CONSTRAINT fk_remittance_funding_transaction
        FOREIGN KEY (funding_financial_transaction_id)
        REFERENCES financial_transactions(id);

ALTER TABLE remittances
    ADD CONSTRAINT fk_remittance_clearing_account
        FOREIGN KEY (clearing_account_id)
        REFERENCES ledger_accounts(id);

ALTER TABLE remittances
    ADD CONSTRAINT chk_remittance_request_hash
        CHECK (request_hash IS NULL OR request_hash ~ '^[0-9a-fA-F]{64}$');

CREATE INDEX idx_remittances_funding_transaction
    ON remittances(funding_financial_transaction_id);

CREATE INDEX idx_remittances_clearing_account
    ON remittances(clearing_account_id);

ALTER TABLE remittance_quotes
    ADD COLUMN remittance_id UUID,
    ADD COLUMN consumed_at TIMESTAMPTZ;

ALTER TABLE remittance_quotes
    ADD CONSTRAINT fk_remittance_quote_remittance
        FOREIGN KEY (remittance_id)
        REFERENCES remittances(id);

CREATE UNIQUE INDEX uq_remittance_quote_consumed
    ON remittance_quotes(remittance_id)
    WHERE remittance_id IS NOT NULL;
