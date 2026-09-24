package com.rackpay.api.domain.ledger;

import com.rackpay.api.domain.money.Currency;

import java.util.Objects;

public record LedgerAccount(LedgerAccountId id, String name, Currency currency, LedgerAccountType type) {
    public LedgerAccount {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(type);
        if (name.isBlank()) throw new IllegalArgumentException("ledger account name must not be blank");
    }
}
