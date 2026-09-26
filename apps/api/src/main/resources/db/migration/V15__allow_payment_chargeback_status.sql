ALTER TABLE payment_transactions
    DROP CONSTRAINT chk_payment_transaction_status;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_transaction_status
    CHECK (status IN (
        'CREATED',
        'PENDING',
        'REQUIRES_ACTION',
        'PAID',
        'FAILED',
        'CANCELLED',
        'REFUNDED',
        'CHARGEBACK'
    ));
