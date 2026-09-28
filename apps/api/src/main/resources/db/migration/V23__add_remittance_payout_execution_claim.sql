ALTER TABLE remittances
    ADD COLUMN payout_execution_claim_token UUID,
    ADD COLUMN payout_execution_claimed_until TIMESTAMPTZ;

CREATE INDEX idx_remittances_payout_execution_claim
    ON remittances(payout_execution_claimed_until);
