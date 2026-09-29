package com.rackpay.api.ledger.core.model;

import com.rackpay.api.shared.core.service.must;

import com.rackpay.api.shared.core.money.Money;

import java.util.Objects;

public record LedgerEntry(LedgerAccountId accountId, Money amount, EntryDirection direction) {
    public LedgerEntry {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(direction);
        if (amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("ledger entry amount must be positive");
    }
}
