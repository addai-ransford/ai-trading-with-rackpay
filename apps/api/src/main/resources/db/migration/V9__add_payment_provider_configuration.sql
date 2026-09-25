CREATE TABLE payment_provider_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    environment VARCHAR(20) NOT NULL,
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_provider_config_provider UNIQUE (provider),
    CONSTRAINT chk_payment_provider_config_provider CHECK (provider IN ('MOLLIE','STRIPE','ADYEN','PAYPAL')),
    CONSTRAINT chk_payment_provider_config_environment CHECK (environment IN ('sandbox','production'))
);

CREATE UNIQUE INDEX uq_payment_provider_config_active
    ON payment_provider_configs(active)
    WHERE active = TRUE;

INSERT INTO payment_provider_configs
    (id, provider, enabled, active, environment)
VALUES
    (gen_random_uuid(), 'MOLLIE', TRUE, TRUE, 'sandbox'),
    (gen_random_uuid(), 'STRIPE', TRUE, FALSE, 'sandbox'),
    (gen_random_uuid(), 'ADYEN', FALSE, FALSE, 'sandbox'),
    (gen_random_uuid(), 'PAYPAL', FALSE, FALSE, 'sandbox');
