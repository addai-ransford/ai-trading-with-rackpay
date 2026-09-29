package com.rackpay.api.remittance;

import com.rackpay.api.remittance.core.model.*;
import com.rackpay.api.remittance.core.service.*;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemittanceEntityTest {

    @Test
    void fundsReservedTransitionStoresFundingMetadata() {
        RemittanceEntity remittance = newRemittance();

        UUID transactionId = UUID.randomUUID();
        UUID clearingAccountId = UUID.randomUUID();
        remittance.markFundsReserved(transactionId, clearingAccountId, Instant.now());

        assertEquals(RemittanceEntity.Status.FUNDS_RESERVED, remittance.getStatus());
        assertEquals(transactionId, remittance.getFundingFinancialTransactionId());
        assertEquals(clearingAccountId, remittance.getClearingAccountId());
    }

    @Test
    void payoutPendingRequiresReservedFunds() {
        RemittanceEntity remittance = newRemittance();

        assertThrows(
            IllegalStateException.class,
            () -> remittance.setPayoutPending("FLUTTERWAVE", "RP-test", Instant.now())
        );
    }

    @Test
    void payoutCanTransitionToCompletedAfterPending() {
        RemittanceEntity remittance = newRemittance();
        remittance.markFundsReserved(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        remittance.setPayoutPending("FLUTTERWAVE", "RP-test", Instant.now());
        remittance.markPayoutCreated(
            "12345",
            RemittanceEntity.Status.COMPLETED,
            Instant.now()
        );

        assertEquals(RemittanceEntity.Status.COMPLETED, remittance.getStatus());
        assertEquals("12345", remittance.getProviderTransferId());
    }

    private static RemittanceEntity newRemittance() {
        return new RemittanceEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            Currency.EUR,
            new BigDecimal("120.00"),
            Currency.GHS,
            new BigDecimal("2.50"),
            Currency.EUR,
            new BigDecimal("1.20"),
            RemittanceEntity.Status.CREATED,
            "idem-test",
            "a".repeat(64),
            Instant.now(),
            Instant.now()
        );
    }
}
