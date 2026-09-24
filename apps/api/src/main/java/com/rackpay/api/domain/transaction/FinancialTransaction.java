package com.rackpay.api.domain.transaction;

import java.time.Instant;
import java.util.Objects;

public final class FinancialTransaction {
    private final TransactionId id;
    private final IdempotencyKey idempotencyKey;
    private final Instant createdAt;
    private TransactionStatus status;

    private FinancialTransaction(TransactionId id, IdempotencyKey idempotencyKey) {
        this.id = Objects.requireNonNull(id);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.createdAt = Instant.now();
        this.status = TransactionStatus.PENDING;
    }

    public static FinancialTransaction create(IdempotencyKey idempotencyKey) {
        return new FinancialTransaction(TransactionId.newId(), idempotencyKey);
    }

    public void startProcessing() {
        requireStatus(TransactionStatus.PENDING);
        status = TransactionStatus.PROCESSING;
    }

    public void complete() {
        requireStatus(TransactionStatus.PROCESSING);
        status = TransactionStatus.COMPLETED;
    }

    public void fail() {
        if (status != TransactionStatus.PROCESSING && status != TransactionStatus.PENDING) {
            throw new IllegalStateException("transaction cannot fail from " + status);
        }
        status = TransactionStatus.FAILED;
    }

    public void cancel() {
        if (status == TransactionStatus.COMPLETED || status == TransactionStatus.CANCELLED) {
            throw new IllegalStateException("transaction cannot be cancelled from " + status);
        }
        status = TransactionStatus.CANCELLED;
    }

    private void requireStatus(TransactionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected status " + expected + " but was " + status);
        }
    }

    public TransactionId id() { return id; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public Instant createdAt() { return createdAt; }
    public TransactionStatus status() { return status; }
}
