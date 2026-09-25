package com.rackpay.api.persistence.wallet;

import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "wallet_balances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_wallet_balance_currency",
        columnNames = {"wallet_id", "currency_code"}
    )
)
public class WalletBalanceEntity {
    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal balance;

    @Column(name = "ledger_account_id", nullable = false, unique = true)
    private UUID ledgerAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletBalanceEntity() {}

    public WalletBalanceEntity(
        UUID id,
        UUID walletId,
        Currency currency,
        BigDecimal balance,
        UUID ledgerAccountId,
        Instant createdAt
    ) {
        this.id = id;
        this.walletId = walletId;
        this.currency = currency;
        this.balance = balance;
        this.ledgerAccountId = ledgerAccountId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public UUID getLedgerAccountId() { return ledgerAccountId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
