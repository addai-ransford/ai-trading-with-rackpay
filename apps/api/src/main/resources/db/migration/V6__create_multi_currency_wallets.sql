CREATE TABLE wallets_v6 (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wallet_owner_v6 UNIQUE (owner_id),
    CONSTRAINT fk_wallet_owner_v6
        FOREIGN KEY (owner_id)
        REFERENCES users(id)
);

CREATE TABLE wallet_balances (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    balance NUMERIC(38, 18) NOT NULL DEFAULT 0,
    ledger_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wallet_balance_currency UNIQUE (wallet_id, currency_code),
    CONSTRAINT uq_wallet_balance_ledger_account UNIQUE (ledger_account_id),
    CONSTRAINT fk_wallet_balance_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets_v6(id),
    CONSTRAINT fk_wallet_balance_ledger_account
        FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_accounts(id),
    CONSTRAINT chk_wallet_balance_non_negative
        CHECK (balance >= 0)
);

INSERT INTO wallets_v6 (id, owner_id, created_at)
SELECT DISTINCT ON (owner_id)
    id,
    owner_id,
    created_at
FROM wallets
ORDER BY owner_id, created_at, id;

INSERT INTO wallet_balances (
    id,
    wallet_id,
    currency_code,
    balance,
    ledger_account_id,
    created_at
)
SELECT
    w.id,
    canonical.id,
    w.currency_code,
    w.balance,
    w.ledger_account_id,
    w.created_at
FROM wallets w
JOIN wallets_v6 canonical
    ON canonical.owner_id = w.owner_id;

DROP TABLE wallets;

ALTER TABLE wallets_v6 RENAME TO wallets;
ALTER TABLE wallets RENAME CONSTRAINT uq_wallet_owner_v6 TO uq_wallet_owner;
ALTER TABLE wallets RENAME CONSTRAINT fk_wallet_owner_v6 TO fk_wallet_owner;

CREATE INDEX idx_wallet_balances_wallet
    ON wallet_balances(wallet_id);

CREATE INDEX idx_wallet_balances_currency
    ON wallet_balances(currency_code);

CREATE INDEX idx_wallet_balances_ledger_account
    ON wallet_balances(ledger_account_id);
