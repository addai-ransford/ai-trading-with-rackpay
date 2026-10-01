ALTER TABLE ledger_accounts
    ADD CONSTRAINT uq_ledger_account_id_currency UNIQUE (id, currency_code);

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entry_account_currency
    FOREIGN KEY (ledger_account_id, currency_code)
    REFERENCES ledger_accounts (id, currency_code);

CREATE OR REPLACE FUNCTION validate_ledger_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    transaction_id UUID;
    debit_total NUMERIC(38, 18);
    credit_total NUMERIC(38, 18);
    entry_count INTEGER;
    currency_count INTEGER;
BEGIN
    transaction_id := COALESCE(NEW.ledger_transaction_id, OLD.ledger_transaction_id);

    SELECT
        COUNT(*),
        COUNT(DISTINCT currency_code),
        COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0),
        COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0)
    INTO entry_count, currency_count, debit_total, credit_total
    FROM ledger_entries
    WHERE ledger_transaction_id = transaction_id;

    IF entry_count < 2 THEN
        RAISE EXCEPTION 'Ledger transaction % must contain at least two entries', transaction_id;
    END IF;

    IF currency_count <> 1 THEN
        RAISE EXCEPTION 'Ledger transaction % must use exactly one currency', transaction_id;
    END IF;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION 'Ledger transaction % is not balanced: debits=% credits=%',
            transaction_id, debit_total, credit_total;
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_validate_ledger_transaction
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_ledger_transaction();
