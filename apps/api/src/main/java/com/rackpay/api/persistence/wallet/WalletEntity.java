package com.rackpay.api.persistence.wallet;

import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uq_wallet_owner_currency", columnNames = {"owner_id", "currency_code"}))
public class WalletEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal balance;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    protected WalletEntity() {}

    public WalletEntity(UUID id, UUID ownerId, Currency currency, BigDecimal balance, Instant createdAt, UUID ledgerAccountId) {
        this.id = id; this.ownerId = ownerId; this.currency = currency; this.balance = balance; this.createdAt = createdAt; this.ledgerAccountId = ledgerAccountId;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getLedgerAccountId() { return ledgerAccountId; }
}
