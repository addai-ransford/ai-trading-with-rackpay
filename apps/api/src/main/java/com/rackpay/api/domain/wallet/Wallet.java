package com.rackpay.api.domain.wallet;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.money.Money;

import java.util.Objects;
import java.util.UUID;

public final class Wallet {
    private final WalletId id;
    private final UUID ownerId;
    private final Currency currency;
    private Money balance;

    private Wallet(WalletId id, UUID ownerId, Currency currency) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.currency = Objects.requireNonNull(currency);
        this.balance = Money.zero(currency);
    }

    public static Wallet open(UUID ownerId, Currency currency) {
        return new Wallet(WalletId.newId(), ownerId, currency);
    }

    public void credit(Money amount) {
        requireCurrency(amount);
        if (amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("credit amount must be positive");
        balance = balance.add(amount);
    }

    public void debit(Money amount) {
        requireCurrency(amount);
        if (amount.isNegative() || amount.isZero()) throw new IllegalArgumentException("debit amount must be positive");
        if (balance.amount().compareTo(amount.amount()) < 0) throw new IllegalStateException("insufficient wallet funds");
        balance = balance.subtract(amount);
    }

    private void requireCurrency(Money amount) {
        if (amount.currency() != currency) throw new IllegalArgumentException("wallet currency mismatch");
    }

    public WalletId id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Currency currency() { return currency; }
    public Money balance() { return balance; }
}
