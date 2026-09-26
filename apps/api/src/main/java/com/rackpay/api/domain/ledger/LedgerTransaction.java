package com.rackpay.api.domain.ledger;

import com.rackpay.api.domain.money.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record LedgerTransaction(UUID id, List<LedgerEntry> entries) {
    public LedgerTransaction {
        Objects.requireNonNull(id);
        List<LedgerEntry> normalizedEntries = List.copyOf(Objects.requireNonNull(entries));

        if (normalizedEntries.size() < 2) {
            throw new IllegalArgumentException("ledger transaction requires at least two entries");
        }
        if (debitTotal(normalizedEntries).compareTo(creditTotal(normalizedEntries)) != 0) {
            throw new IllegalArgumentException("ledger transaction must balance");
        }
        if (currencies(normalizedEntries).size() != 1) {
            throw new IllegalArgumentException("ledger transaction must use one currency");
        }

        entries = normalizedEntries;
    }

    public static LedgerTransaction create(List<LedgerEntry> entries) {
        return new LedgerTransaction(UUID.randomUUID(), entries);
    }

    private static BigDecimal debitTotal(List<LedgerEntry> entries) {
        return entries.stream()
            .filter(e -> e.direction() == EntryDirection.DEBIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal creditTotal(List<LedgerEntry> entries) {
        return entries.stream()
            .filter(e -> e.direction() == EntryDirection.CREDIT)
            .map(e -> e.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Set<Currency> currencies(List<LedgerEntry> entries) {
        return entries.stream()
            .map(e -> e.amount().currency())
            .collect(Collectors.toSet());
    }
}
