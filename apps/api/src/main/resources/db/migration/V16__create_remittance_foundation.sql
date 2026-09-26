CREATE TABLE remittance_countries (
    code VARCHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    dial_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    send_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    receive_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_remittance_country_code CHECK (code = UPPER(code)),
    CONSTRAINT chk_remittance_country_dial_code CHECK (dial_code ~ '^\\+[0-9]{1,7}$')
);
CREATE TABLE mobile_money_networks (
    id UUID PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL REFERENCES remittance_countries(code),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mobile_money_network_country_code UNIQUE (country_code, code)
);
CREATE TABLE remittance_corridors (
    id UUID PRIMARY KEY,
    source_country_code VARCHAR(2) NOT NULL REFERENCES remittance_countries(code),
    destination_country_code VARCHAR(2) NOT NULL REFERENCES remittance_countries(code),
    source_currency_code VARCHAR(3) NOT NULL,
    destination_currency_code VARCHAR(3) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    min_amount NUMERIC(38,18) NOT NULL,
    max_amount NUMERIC(38,18),
    fee_fixed_amount NUMERIC(38,18) NOT NULL DEFAULT 0,
    fee_currency_code VARCHAR(3) NOT NULL,
    fee_bps INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_remittance_corridor UNIQUE (source_country_code,destination_country_code,source_currency_code,destination_currency_code),
    CONSTRAINT chk_remittance_corridor_min CHECK (min_amount > 0),
    CONSTRAINT chk_remittance_corridor_max CHECK (max_amount IS NULL OR max_amount >= min_amount),
    CONSTRAINT chk_remittance_corridor_fee_fixed CHECK (fee_fixed_amount >= 0),
    CONSTRAINT chk_remittance_corridor_fee_bps CHECK (fee_bps >= 0 AND fee_bps <= 10000)
);
CREATE TABLE remittance_recipients (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    country_code VARCHAR(2) NOT NULL REFERENCES remittance_countries(code),
    payout_method VARCHAR(30) NOT NULL,
    mobile_money_network_id UUID REFERENCES mobile_money_networks(id),
    phone_number VARCHAR(32),
    normalized_phone_number VARCHAR(32),
    verified_name VARCHAR(255),
    verification_provider VARCHAR(30),
    provider_recipient_reference VARCHAR(255),
    verified_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_remittance_recipient_method CHECK (payout_method IN ('BANK_ACCOUNT','MOBILE_MONEY')),
    CONSTRAINT chk_remittance_recipient_mobile_data CHECK (payout_method <> 'MOBILE_MONEY' OR (mobile_money_network_id IS NOT NULL AND phone_number IS NOT NULL AND normalized_phone_number IS NOT NULL))
);
CREATE INDEX idx_remittance_recipients_user ON remittance_recipients(user_id, created_at DESC);
CREATE INDEX idx_remittance_recipients_lookup ON remittance_recipients(country_code, normalized_phone_number);
CREATE TABLE remittances (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    corridor_id UUID NOT NULL REFERENCES remittance_corridors(id),
    recipient_id UUID NOT NULL REFERENCES remittance_recipients(id),
    source_amount NUMERIC(38,18) NOT NULL,
    source_currency_code VARCHAR(3) NOT NULL,
    destination_amount NUMERIC(38,18) NOT NULL,
    destination_currency_code VARCHAR(3) NOT NULL,
    fee_amount NUMERIC(38,18) NOT NULL DEFAULT 0,
    fee_currency_code VARCHAR(3) NOT NULL,
    fx_rate NUMERIC(38,18) NOT NULL,
    payout_provider VARCHAR(30),
    provider_transfer_id VARCHAR(255),
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_remittance_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_remittance_provider_transfer UNIQUE (payout_provider, provider_transfer_id),
    CONSTRAINT chk_remittance_source_amount CHECK (source_amount > 0),
    CONSTRAINT chk_remittance_destination_amount CHECK (destination_amount > 0),
    CONSTRAINT chk_remittance_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT chk_remittance_fx_rate CHECK (fx_rate > 0),
    CONSTRAINT chk_remittance_status CHECK (status IN ('CREATED','RECIPIENT_VERIFIED','QUOTED','FUNDS_RESERVED','PAYOUT_PENDING','PAYOUT_PROCESSING','COMPLETED','FAILED','CANCELLED','RECOVERY_REQUIRED'))
);
CREATE INDEX idx_remittances_user ON remittances(user_id, created_at DESC);
CREATE INDEX idx_remittances_status ON remittances(status, created_at DESC);
