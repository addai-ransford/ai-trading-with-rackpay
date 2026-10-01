CREATE TABLE remittance_payout_attempts (
    id UUID PRIMARY KEY,
    remittance_id UUID NOT NULL REFERENCES remittances(id),
    attempt_number INTEGER NOT NULL,
    provider VARCHAR(30) NOT NULL,
    payout_reference VARCHAR(255) NOT NULL,
    provider_transfer_id VARCHAR(255),
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_remittance_payout_attempt_number UNIQUE (remittance_id, attempt_number),
    CONSTRAINT uq_remittance_payout_attempt_reference UNIQUE (provider, payout_reference),
    CONSTRAINT uq_remittance_payout_attempt_transfer UNIQUE (provider, provider_transfer_id),
    CONSTRAINT chk_remittance_payout_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_remittance_payout_attempt_status CHECK (
        status IN ('CREATED','PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED','UNKNOWN')
    )
);

CREATE INDEX idx_remittance_payout_attempts_remittance
    ON remittance_payout_attempts(remittance_id, attempt_number);

CREATE INDEX idx_remittance_payout_attempts_status
    ON remittance_payout_attempts(status, updated_at);
