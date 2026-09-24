package com.rackpay.api.persistence.ledger;

import com.rackpay.api.domain.ledger.EntryDirection;
import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_transaction_id", nullable = false)
    private LedgerTransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false)
    private LedgerAccountEntity account;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryDirection direction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {}

    public LedgerEntryEntity(UUID id, LedgerAccountEntity account, BigDecimal amount,
                             Currency currency, EntryDirection direction, Instant createdAt) {
        this.id = id;
        this.account = account;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.createdAt = createdAt;
    }

    void attachTo(LedgerTransactionEntity transaction) {
        this.transaction = transaction;
    }

    public UUID getId() { return id; }
    public LedgerTransactionEntity getTransaction() { return transaction; }
    public LedgerAccountEntity getAccount() { return account; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public EntryDirection getDirection() { return direction; }
    public Instant getCreatedAt() { return createdAt; }
}
