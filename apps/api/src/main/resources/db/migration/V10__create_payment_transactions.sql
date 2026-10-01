CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    financial_transaction_id UUID,
    user_id UUID NOT NULL REFERENCES users(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    provider VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(38,18) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_transaction_provider_payment UNIQUE (provider, provider_payment_id),
    CONSTRAINT fk_payment_transaction_financial FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT chk_payment_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_transaction_status CHECK (status IN ('CREATED','PENDING','REQUIRES_ACTION','PAID','FAILED','CANCELLED','REFUNDED')),
    CONSTRAINT chk_payment_transaction_provider CHECK (provider IN ('MOLLIE','STRIPE','ADYEN','PAYPAL'))
);

CREATE INDEX idx_payment_transactions_user ON payment_transactions(user_id, created_at DESC);
CREATE INDEX idx_payment_transactions_wallet ON payment_transactions(wallet_id, created_at DESC);
CREATE INDEX idx_payment_transactions_provider_payment ON payment_transactions(provider, provider_payment_id);
