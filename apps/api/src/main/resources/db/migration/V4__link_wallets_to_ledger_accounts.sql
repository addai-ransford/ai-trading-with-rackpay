ALTER TABLE wallets
    ADD COLUMN ledger_account_id UUID;

ALTER TABLE wallets
    ADD CONSTRAINT uq_wallet_ledger_account
    UNIQUE (ledger_account_id);

ALTER TABLE wallets
    ADD CONSTRAINT fk_wallet_ledger_account
    FOREIGN KEY (ledger_account_id)
    REFERENCES ledger_accounts (id);
