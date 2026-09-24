package com.rackpay.api.persistence.transaction;

import com.rackpay.api.domain.transaction.TransactionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions",
       uniqueConstraints = @UniqueConstraint(name = "uq_financial_transaction_idempotency",
                                             columnNames = "idempotency_key"))
public class FinancialTransactionEntity {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialTransactionEntity() {}

    public FinancialTransactionEntity(UUID id, String idempotencyKey, String requestHash,
                                      TransactionStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public TransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markProcessing(Instant now) {
        requireStatus(TransactionStatus.PENDING);
        status = TransactionStatus.PROCESSING;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        requireStatus(TransactionStatus.PROCESSING);
        status = TransactionStatus.COMPLETED;
        updatedAt = now;
    }

    public void markFailed(Instant now) {
        if (status != TransactionStatus.PENDING && status != TransactionStatus.PROCESSING) {
            throw new IllegalStateException("transaction cannot fail from " + status);
        }
        status = TransactionStatus.FAILED;
        updatedAt = now;
    }

    private void requireStatus(TransactionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                "expected status " + expected + " but was " + status
            );
        }
    }
}
