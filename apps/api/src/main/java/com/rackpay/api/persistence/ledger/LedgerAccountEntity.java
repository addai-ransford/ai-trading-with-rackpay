package com.rackpay.api.persistence.ledger;

import com.rackpay.api.domain.ledger.LedgerAccountType;
import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccountEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3)
    private Currency currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private LedgerAccountType type;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccountEntity() {}

    public LedgerAccountEntity(UUID id, String name, Currency currency, LedgerAccountType type, Instant createdAt) {
        this.id=id; this.name=name; this.currency=currency; this.type=type; this.createdAt=createdAt;
    }
    public UUID getId(){return id;}
    public String getName(){return name;}
    public Currency getCurrency(){return currency;}
    public LedgerAccountType getType(){return type;}
    public Instant getCreatedAt(){return createdAt;}
}
