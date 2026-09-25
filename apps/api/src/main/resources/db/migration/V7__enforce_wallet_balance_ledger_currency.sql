ALTER TABLE wallet_balances
    ADD CONSTRAINT fk_wallet_balance_ledger_account_currency
    FOREIGN KEY (ledger_account_id, currency_code)
    REFERENCES ledger_accounts (id, currency_code);
