CREATE TABLE users (
    id UUID PRIMARY KEY,
    keycloak_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_keycloak_subject UNIQUE (keycloak_subject),
    CONSTRAINT uq_user_email UNIQUE (email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_users_email ON users(email);

ALTER TABLE wallets
    ADD CONSTRAINT fk_wallet_owner
    FOREIGN KEY (owner_id)
    REFERENCES users(id);

CREATE UNIQUE INDEX uq_wallet_ledger_account_nonnull
    ON wallets(ledger_account_id)
    WHERE ledger_account_id IS NOT NULL;
