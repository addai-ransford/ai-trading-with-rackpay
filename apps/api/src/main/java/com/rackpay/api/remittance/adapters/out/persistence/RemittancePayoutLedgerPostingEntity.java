package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.ledger.core.model.LedgerTransaction;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.shared.core.transaction.TransactionId;

import com.rackpay.api.shared.core.money.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remittance_payout_ledger_postings",
    uniqueConstraints = @UniqueConstraint(name = "uq_remittance_payout_ledger_posting_remittance", columnNames = "remittance_id"))
public class RemittancePayoutLedgerPostingEntity {
    @Id
    private UUID id;

    @Column(name = "remittance_id", nullable = false)
    private UUID remittanceId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_transfer_id", length = 255)
    private String providerTransferId;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "clearing_account_id", nullable = false)
    private UUID clearingAccountId;

    @Column(name = "settlement_account_id", nullable = false)
    private UUID settlementAccountId;

    @Column(name = "ledger_transaction_id", nullable = false)
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RemittancePayoutLedgerPostingEntity() {}

    public RemittancePayoutLedgerPostingEntity(
        UUID id, UUID remittanceId, String provider, String providerTransferId,
        BigDecimal amount, Currency currency, UUID clearingAccountId,
        UUID settlementAccountId, UUID ledgerTransactionId, Instant createdAt
    ) {
        this.id = id;
        this.remittanceId = remittanceId;
        this.provider = provider;
        this.providerTransferId = providerTransferId;
        this.amount = amount;
        this.currency = currency;
        this.clearingAccountId = clearingAccountId;
        this.settlementAccountId = settlementAccountId;
        this.ledgerTransactionId = ledgerTransactionId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRemittanceId() { return remittanceId; }
    public String getProvider() { return provider; }
    public String getProviderTransferId() { return providerTransferId; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public UUID getClearingAccountId() { return clearingAccountId; }
    public UUID getSettlementAccountId() { return settlementAccountId; }
    public UUID getLedgerTransactionId() { return ledgerTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
}