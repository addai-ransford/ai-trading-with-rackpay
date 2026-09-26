package com.rackpay.api.persistence.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.transaction.TransactionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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

    @Column(name = "wallet_id")
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 20)
    private OperationType operationType;

    @Column(precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", length = 3)
    private Currency currency;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

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
    public UUID getWalletId() { return walletId; }
    public OperationType getOperationType() { return operationType; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public UUID getLedgerTransactionId() { return ledgerTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void attachWalletOperation(UUID walletId, OperationType operationType,
                                      BigDecimal amount, Currency currency,
                                      UUID ledgerTransactionId) {
        if (this.walletId != null || this.operationType != null || this.amount != null
            || this.currency != null || this.ledgerTransactionId != null) {
            throw new IllegalStateException("financial transaction operation is already linked");
        }
        if (walletId == null || operationType == null || amount == null || amount.signum() <= 0
            || currency == null || ledgerTransactionId == null) {
            throw new IllegalArgumentException("wallet operation metadata is invalid");
        }

        this.walletId = walletId;
        this.operationType = operationType;
        this.amount = amount;
        this.currency = currency;
        this.ledgerTransactionId = ledgerTransactionId;
    }

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

    public enum OperationType {
        CREDIT,
        DEBIT
    }
}
