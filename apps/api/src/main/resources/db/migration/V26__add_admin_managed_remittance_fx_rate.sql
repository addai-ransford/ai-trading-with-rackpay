ALTER TABLE remittance_corridors
    ADD COLUMN fx_rate NUMERIC(38,18);

ALTER TABLE remittance_corridors
    ADD CONSTRAINT chk_remittance_corridor_fx_rate
        CHECK (fx_rate IS NULL OR fx_rate > 0);
