package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.remittance.ports.out.PayoutProvider;

import java.time.Instant;
import java.util.UUID;

import com.rackpay.api.remittance.core.model.PayoutProviderType;

import jakarta.persistence.*;

@Entity
@Table(
    name = "remittance_payout_attempts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_remittance_payout_attempt_number", columnNames = {"remittance_id", "attempt_number"}),
        @UniqueConstraint(name = "uq_remittance_payout_attempt_reference", columnNames = {"provider", "payout_reference"}),
        @UniqueConstraint(name = "uq_remittance_payout_attempt_transfer", columnNames = {"provider", "provider_transfer_id"})
    }
)
public class RemittancePayoutAttemptEntity {
    @Id private UUID id;
    @Column(name = "remittance_id", nullable = false) private UUID remittanceId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PayoutProviderType provider;
    @Column(name = "payout_reference", nullable = false, length = 255) private String payoutReference;
    @Column(name = "provider_transfer_id", length = 255) private String providerTransferId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Status status;
    @Column(name = "failure_reason", length = 1000) private String failureReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected RemittancePayoutAttemptEntity() {}

    public RemittancePayoutAttemptEntity(UUID id, UUID remittanceId, int attemptNumber,
        PayoutProviderType provider, String payoutReference, Instant now) {
        this.id = id;
        this.remittanceId = remittanceId;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.payoutReference = payoutReference;
        this.status = Status.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRemittanceId() { return remittanceId; }
    public int getAttemptNumber() { return attemptNumber; }
    public PayoutProviderType getProvider() { return provider; }
    public String getPayoutReference() { return payoutReference; }
    public String getProviderTransferId() { return providerTransferId; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void record(String providerTransferId, Status status, String failureReason, Instant now) {
        this.providerTransferId = providerTransferId;
        this.status = status;
        this.failureReason = failureReason;
        this.updatedAt = now;
        this.completedAt = status == Status.COMPLETED ? now : null;
    }

    public enum Status {
        CREATED, PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, UNKNOWN
    }
}
