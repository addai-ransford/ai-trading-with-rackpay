package com.rackpay.api.domain.ledger;

import com.rackpay.api.domain.money.Money;

import java.util.Objects;

public record LedgerEntry(LedgerAccountId accountId, Money amount, EntryDirection direction) {
    public LedgerEntry {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(direction);
        if (amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("ledger entry amount must be positive");
    }
}
