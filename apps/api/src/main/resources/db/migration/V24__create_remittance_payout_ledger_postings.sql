CREATE TABLE remittance_payout_ledger_postings (
    id UUID PRIMARY KEY,
    remittance_id UUID NOT NULL REFERENCES remittances(id),
    provider VARCHAR(30) NOT NULL,
    provider_transfer_id VARCHAR(255),
    amount NUMERIC(38,18) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    clearing_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    settlement_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_remittance_payout_ledger_posting_remittance UNIQUE (remittance_id),
    CONSTRAINT chk_remittance_payout_ledger_posting_amount CHECK (amount > 0)
);

CREATE INDEX idx_remittance_payout_ledger_postings_provider
    ON remittance_payout_ledger_postings(provider, created_at);

CREATE INDEX idx_remittance_payout_ledger_postings_transfer
    ON remittance_payout_ledger_postings(provider, provider_transfer_id);
