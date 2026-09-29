package com.rackpay.api.remittance;

import com.rackpay.api.remittance.core.model.*;
import com.rackpay.api.remittance.core.service.*;

import com.rackpay.api.remittance.adapters.out.persistence.RemittancePayoutAttemptEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RemittancePayoutAttemptEntityTest {

    @Test
    void recordsCompletedAttemptAndCompletionTime() {
        Instant created = Instant.parse("2026-09-28T08:00:00Z");
        Instant completed = Instant.parse("2026-09-28T08:01:00Z");

        RemittancePayoutAttemptEntity attempt = new RemittancePayoutAttemptEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            PayoutProviderType.FLUTTERWAVE,
            "rp-test-1",
            created
        );

        attempt.record(
            "fw-transfer-1",
            RemittancePayoutAttemptEntity.Status.COMPLETED,
            null,
            completed
        );

        assertEquals(RemittancePayoutAttemptEntity.Status.COMPLETED, attempt.getStatus());
        assertEquals("fw-transfer-1", attempt.getProviderTransferId());
        assertEquals(completed, attempt.getCompletedAt());
        assertEquals(completed, attempt.getUpdatedAt());
    }

    @Test
    void nonTerminalAttemptHasNoCompletionTime() {
        Instant created = Instant.parse("2026-09-28T08:00:00Z");
        Instant updated = Instant.parse("2026-09-28T08:01:00Z");

        RemittancePayoutAttemptEntity attempt = new RemittancePayoutAttemptEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            PayoutProviderType.FLUTTERWAVE,
            "rp-test-1",
            created
        );

        attempt.record(
            "fw-transfer-1",
            RemittancePayoutAttemptEntity.Status.PENDING,
            null,
            updated
        );

        assertEquals(RemittancePayoutAttemptEntity.Status.PENDING, attempt.getStatus());
        assertNull(attempt.getCompletedAt());
    }
}
