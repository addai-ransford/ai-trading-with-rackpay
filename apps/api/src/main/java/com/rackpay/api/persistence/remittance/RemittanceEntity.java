package com.rackpay.api.persistence.remittance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.rackpay.api.domain.money.Currency;

import jakarta.persistence.*;

@Entity
@Table(name = "remittances", uniqueConstraints = @UniqueConstraint(
    name = "uq_remittance_user_idempotency",
    columnNames = {"user_id", "idempotency_key"}
))
public class RemittanceEntity {
    @Id private UUID id;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="wallet_id",nullable=false) private UUID walletId;
    @Column(name="corridor_id",nullable=false) private UUID corridorId;
    @Column(name="recipient_id",nullable=false) private UUID recipientId;
    @Column(name="source_amount",nullable=false,precision=38,scale=18) private BigDecimal sourceAmount;
    @Enumerated(EnumType.STRING) @Column(name="source_currency_code",nullable=false,length=3) private Currency sourceCurrency;
    @Column(name="destination_amount",nullable=false,precision=38,scale=18) private BigDecimal destinationAmount;
    @Enumerated(EnumType.STRING) @Column(name="destination_currency_code",nullable=false,length=3) private Currency destinationCurrency;
    @Column(name="fee_amount",nullable=false,precision=38,scale=18) private BigDecimal feeAmount;
    @Enumerated(EnumType.STRING) @Column(name="fee_currency_code",nullable=false,length=3) private Currency feeCurrency;
    @Column(name="fx_rate",nullable=false,precision=38,scale=18) private BigDecimal fxRate;
    @Column(name="payout_provider",length=30) private String payoutProvider;
    @Column(name="payout_reference",length=255) private String payoutReference;
    @Column(name="provider_transfer_id",length=255) private String providerTransferId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Status status;
    @Column(name="idempotency_key",nullable=false,length=255) private String idempotencyKey;
    @Column(name="request_hash",length=64) private String requestHash;
    @Column(name="funding_financial_transaction_id") private UUID fundingFinancialTransactionId;
    @Column(name="clearing_account_id") private UUID clearingAccountId;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="payout_execution_claim_token") private UUID payoutExecutionClaimToken;
    @Column(name="payout_execution_claimed_until") private Instant payoutExecutionClaimedUntil;

    protected RemittanceEntity() {}

    public RemittanceEntity(UUID id, UUID userId, UUID walletId, UUID corridorId, UUID recipientId,
        BigDecimal sourceAmount, Currency sourceCurrency, BigDecimal destinationAmount,
        Currency destinationCurrency, BigDecimal feeAmount, Currency feeCurrency, BigDecimal fxRate,
        Status status, String idempotencyKey, String requestHash, Instant createdAt, Instant updatedAt) {
        this.id=id; this.userId=userId; this.walletId=walletId; this.corridorId=corridorId; this.recipientId=recipientId;
        this.sourceAmount=sourceAmount; this.sourceCurrency=sourceCurrency; this.destinationAmount=destinationAmount;
        this.destinationCurrency=destinationCurrency; this.feeAmount=feeAmount; this.feeCurrency=feeCurrency;
        this.fxRate=fxRate; this.status=status; this.idempotencyKey=idempotencyKey; this.requestHash=requestHash;
        this.createdAt=createdAt; this.updatedAt=updatedAt;
    }

    public UUID getId(){return id;}
    public UUID getUserId(){return userId;}
    public UUID getWalletId(){return walletId;}
    public UUID getCorridorId(){return corridorId;}
    public UUID getRecipientId(){return recipientId;}
    public BigDecimal getSourceAmount(){return sourceAmount;}
    public Currency getSourceCurrency(){return sourceCurrency;}
    public BigDecimal getDestinationAmount(){return destinationAmount;}
    public Currency getDestinationCurrency(){return destinationCurrency;}
    public BigDecimal getFeeAmount(){return feeAmount;}
    public Currency getFeeCurrency(){return feeCurrency;}
    public BigDecimal getFxRate(){return fxRate;}
    public String getPayoutProvider(){return payoutProvider;}
    public String getPayoutReference(){return payoutReference;}
    public String getProviderTransferId(){return providerTransferId;}
    public Status getStatus(){return status;}
    public String getIdempotencyKey(){return idempotencyKey;}
    public String getRequestHash(){return requestHash;}
    public UUID getFundingFinancialTransactionId(){return fundingFinancialTransactionId;}
    public UUID getClearingAccountId(){return clearingAccountId;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public UUID getPayoutExecutionClaimToken(){return payoutExecutionClaimToken;}
    public Instant getPayoutExecutionClaimedUntil(){return payoutExecutionClaimedUntil;}

    public boolean hasActivePayoutExecutionClaim(Instant now) {
        return payoutExecutionClaimToken != null
            && payoutExecutionClaimedUntil != null
            && payoutExecutionClaimedUntil.isAfter(now);
    }

    public void claimPayoutExecution(UUID token, Instant claimedUntil, Instant now) {
        if (hasActivePayoutExecutionClaim(now)) {
            throw new IllegalStateException("remittance payout is already being processed");
        }
        this.payoutExecutionClaimToken = token;
        this.payoutExecutionClaimedUntil = claimedUntil;
        this.updatedAt = now;
    }

    public void clearPayoutExecutionClaim(Instant now) {
        this.payoutExecutionClaimToken = null;
        this.payoutExecutionClaimedUntil = null;
        this.updatedAt = now;
    }

    public void setPayoutPending(String payoutProvider, String payoutReference, Instant now) {
        requireStatus(Status.FUNDS_RESERVED);
        beginPayoutAttempt(payoutProvider, payoutReference, now);
    }

    public void beginPayoutAttempt(String payoutProvider, String payoutReference, Instant now) {
        if (status != Status.FUNDS_RESERVED && status != Status.PAYOUT_PENDING && status != Status.PAYOUT_PROCESSING) {
            throw new IllegalStateException("remittance cannot start payout from " + status);
        }
        this.payoutProvider=payoutProvider;
        this.payoutReference=payoutReference;
        this.providerTransferId=null;
        this.status=Status.PAYOUT_PENDING;
        this.updatedAt=now;
    }

    public void markPayoutCreated(String providerTransferId, Status payoutStatus, Instant now) {
        if (status != Status.PAYOUT_PENDING && status != Status.PAYOUT_PROCESSING) {
            throw new IllegalStateException("remittance cannot record payout from " + status);
        }
        this.providerTransferId=providerTransferId;
        this.status=payoutStatus;
        this.updatedAt=now;
    }

    public void markPayoutStatus(Status payoutStatus, Instant now) {
        if (status != Status.PAYOUT_PENDING && status != Status.PAYOUT_PROCESSING) {
            throw new IllegalStateException("remittance cannot update payout from " + status);
        }
        this.status=payoutStatus;
        this.updatedAt=now;
    }

    public void markFundsReserved(UUID fundingFinancialTransactionId, UUID clearingAccountId, Instant now) {
        requireStatus(Status.CREATED);
        this.fundingFinancialTransactionId=fundingFinancialTransactionId;
        this.clearingAccountId=clearingAccountId;
        this.status=Status.FUNDS_RESERVED;
        this.updatedAt=now;
    }

    public enum Status {
        CREATED, RECIPIENT_VERIFIED, QUOTED, FUNDS_RESERVED, PAYOUT_PENDING, PAYOUT_PROCESSING,
        COMPLETED, FAILED, CANCELLED, RECOVERY_REQUIRED
    }

    private void requireStatus(Status expected) {
        if (status != expected) throw new IllegalStateException(
            "remittance must be in " + expected + " status, but is " + status
        );
    }
}
