package com.rackpay.api.persistence.transaction;

import com.rackpay.api.domain.transaction.TransactionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions", uniqueConstraints = @UniqueConstraint(name = "uq_financial_transaction_idempotency", columnNames = "idempotency_key"))
public class FinancialTransactionEntity {
    @Id
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialTransactionEntity() {}

    public FinancialTransactionEntity(UUID id, String idempotencyKey, TransactionStatus status, Instant createdAt, Instant updatedAt) {
        this.id=id; this.idempotencyKey=idempotencyKey; this.status=status; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId(){return id;}
    public String getIdempotencyKey(){return idempotencyKey;}
    public TransactionStatus getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}
