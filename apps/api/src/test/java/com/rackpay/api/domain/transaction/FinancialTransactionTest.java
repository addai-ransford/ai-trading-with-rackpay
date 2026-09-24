package com.rackpay.api.domain.transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinancialTransactionTest {
    @Test
    void followsProcessingLifecycle() {
        var tx = FinancialTransaction.create(new IdempotencyKey("payment-123"));

        tx.startProcessing();
        tx.complete();

        assertEquals(TransactionStatus.COMPLETED, tx.status());
    }

    @Test
    void cannotProcessCompletedTransactionAgain() {
        var tx = FinancialTransaction.create(new IdempotencyKey("payment-456"));
        tx.startProcessing();
        tx.complete();

        assertThrows(IllegalStateException.class, tx::startProcessing);
    }
}
