CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    balance NUMERIC(38, 18) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT uq_wallet_owner_currency UNIQUE (owner_id, currency_code)
);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_account_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'))
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    amount NUMERIC(38, 18) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries(ledger_transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(ledger_account_id);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_financial_transaction_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_financial_transaction_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_financial_transactions_status ON financial_transactions(status);
