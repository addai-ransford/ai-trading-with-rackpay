CREATE TABLE remittance_quotes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    corridor_id UUID NOT NULL REFERENCES remittance_corridors(id),
    recipient_id UUID NOT NULL REFERENCES remittance_recipients(id),
    source_amount NUMERIC(38,18) NOT NULL,
    source_currency_code VARCHAR(3) NOT NULL,
    destination_amount NUMERIC(38,18) NOT NULL,
    destination_currency_code VARCHAR(3) NOT NULL,
    fee_amount NUMERIC(38,18) NOT NULL DEFAULT 0,
    fee_currency_code VARCHAR(3) NOT NULL,
    fx_rate NUMERIC(38,18) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_remittance_quote_source_amount CHECK (source_amount > 0),
    CONSTRAINT chk_remittance_quote_destination_amount CHECK (destination_amount > 0),
    CONSTRAINT chk_remittance_quote_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT chk_remittance_quote_fx_rate CHECK (fx_rate > 0)
);

CREATE INDEX idx_remittance_quotes_user ON remittance_quotes(user_id, created_at DESC);
CREATE INDEX idx_remittance_quotes_expiry ON remittance_quotes(expires_at);
