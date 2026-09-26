CREATE TABLE payment_adjustments (
    id UUID PRIMARY KEY,
    payment_transaction_id UUID NOT NULL REFERENCES payment_transactions(id),
    provider VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(255) NOT NULL,
    adjustment_type VARCHAR(30) NOT NULL,
    amount NUMERIC(38,18) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    financial_transaction_id UUID REFERENCES financial_transactions(id),
    provider_event_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_adjustment_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT uq_payment_adjustment_payment_type UNIQUE (payment_transaction_id, adjustment_type),
    CONSTRAINT chk_payment_adjustment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_adjustment_type CHECK (adjustment_type IN ('REFUND','CHARGEBACK')),
    CONSTRAINT chk_payment_adjustment_status CHECK (status IN ('PENDING','COMPLETED','PENDING_RECOVERY','FAILED')),
    CONSTRAINT chk_payment_adjustment_provider CHECK (provider IN ('MOLLIE','STRIPE','ADYEN','PAYPAL'))
);

CREATE INDEX idx_payment_adjustments_payment
    ON payment_adjustments(payment_transaction_id, created_at DESC);

CREATE INDEX idx_payment_adjustments_recovery
    ON payment_adjustments(status, created_at DESC);
