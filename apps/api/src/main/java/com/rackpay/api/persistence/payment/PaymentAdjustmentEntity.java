package com.rackpay.api.persistence.payment;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "payment_adjustments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_payment_adjustment_provider_event",
            columnNames = {"provider", "provider_event_id"}
        ),
        @UniqueConstraint(
            name = "uq_payment_adjustment_payment_type",
            columnNames = {"payment_transaction_id", "adjustment_type"}
        )
    }
)
public class PaymentAdjustmentEntity {
    public enum Type { REFUND, CHARGEBACK }
    public enum Status { PENDING, COMPLETED, PENDING_RECOVERY, FAILED }

    @Id
    private UUID id;

    @Column(name = "payment_transaction_id", nullable = false)
    private UUID paymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProviderType provider;

    @Column(name = "provider_payment_id", nullable = false, length = 255)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private Type adjustmentType;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAdjustmentEntity() {}

    public PaymentAdjustmentEntity(
        UUID id,
        UUID paymentTransactionId,
        PaymentProviderType provider,
        String providerPaymentId,
        Type adjustmentType,
        BigDecimal amount,
        Currency currency,
        String providerEventId,
        Instant now
    ) {
        this.id = id;
        this.paymentTransactionId = paymentTransactionId;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.adjustmentType = adjustmentType;
        this.amount = amount;
        this.currency = currency;
        this.status = Status.PENDING;
        this.providerEventId = providerEventId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public PaymentProviderType getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Type getAdjustmentType() { return adjustmentType; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public UUID getFinancialTransactionId() { return financialTransactionId; }
    public String getProviderEventId() { return providerEventId; }

    public void complete(UUID financialTransactionId, Instant now) {
        this.financialTransactionId = financialTransactionId;
        this.status = Status.COMPLETED;
        this.updatedAt = now;
    }

    public void markPendingRecovery(Instant now) {
        this.status = Status.PENDING_RECOVERY;
        this.updatedAt = now;
    }

    public void markFailed(Instant now) {
        this.status = Status.FAILED;
        this.updatedAt = now;
    }
}
