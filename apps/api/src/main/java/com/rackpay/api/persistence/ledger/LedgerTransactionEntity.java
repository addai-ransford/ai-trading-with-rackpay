package com.rackpay.api.persistence.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity {
    @Id
    private UUID id;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerEntryEntity> entries = new ArrayList<>();

    protected LedgerTransactionEntity() {}

    public LedgerTransactionEntity(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public void addEntry(LedgerEntryEntity entry) {
        entries.add(entry);
        entry.attachTo(this);
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerEntryEntity> getEntries() { return Collections.unmodifiableList(entries); }
}
