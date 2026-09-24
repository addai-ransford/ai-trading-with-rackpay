package com.rackpay.api.domain.ledger;

import com.rackpay.api.domain.money.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransaction(UUID id, List<LedgerEntry> entries) {
    public LedgerTransaction {
        Objects.requireNonNull(id);
        entries = List.copyOf(Objects.requireNonNull(entries));
        if (entries.size() < 2) throw new IllegalArgumentException("ledger transaction requires at least two entries");
        if (debitTotal() .compareTo(creditTotal()) != 0) throw new IllegalArgumentException("ledger transaction must balance");
        if (currencies().size() != 1) throw new IllegalArgumentException("ledger transaction must use one currency");
    }

    public static LedgerTransaction create(List<LedgerEntry> entries) {
        return new LedgerTransaction(UUID.randomUUID(), entries);
    }

    private BigDecimal debitTotal() {
        return entries.stream().filter(e -> e.direction() == EntryDirection.DEBIT).map(e -> e.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal creditTotal() {
        return entries.stream().filter(e -> e.direction() == EntryDirection.CREDIT).map(e -> e.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private java.util.Set<Currency> currencies() {
        return entries.stream().map(e -> e.amount().currency()).collect(java.util.stream.Collectors.toSet());
    }
}
