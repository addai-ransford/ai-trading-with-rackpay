package com.rackpay.api.domain.wallet;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.money.Money;

import java.util.*;

public final class Wallet {
    private final WalletId id;
    private final UUID ownerId;
    private final Map<Currency, Money> balances;

    private Wallet(WalletId id, UUID ownerId) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.balances = new EnumMap<>(Currency.class);
    }

    public static Wallet open(UUID ownerId) {
        return new Wallet(WalletId.newId(), ownerId);
    }

    public void openCurrency(Currency currency) {
        balances.putIfAbsent(
            Objects.requireNonNull(currency),
            Money.zero(currency)
        );
    }

    public void credit(Money amount) {
        requireCurrencyBalance(amount);
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("credit amount must be positive");
        }
        balances.put(amount.currency(), balances.get(amount.currency()).add(amount));
    }

    public void debit(Money amount) {
        requireCurrencyBalance(amount);
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("debit amount must be positive");
        }
        Money balance = balances.get(amount.currency());
        if (balance.amount().compareTo(amount.amount()) < 0) {
            throw new IllegalStateException("insufficient wallet funds");
        }
        balances.put(amount.currency(), balance.subtract(amount));
    }

    public Money balance(Currency currency) {
        return balances.getOrDefault(
            Objects.requireNonNull(currency),
            Money.zero(currency)
        );
    }

    private void requireCurrencyBalance(Money amount) {
        Objects.requireNonNull(amount);
        if (!balances.containsKey(amount.currency())) {
            throw new IllegalStateException(
                "wallet has no " + amount.currency() + " balance"
            );
        }
    }

    public WalletId id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Map<Currency, Money> balances() {
        return Collections.unmodifiableMap(balances);
    }
}
