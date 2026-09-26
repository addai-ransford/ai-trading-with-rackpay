package com.rackpay.api.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.scale() > 18) {
            throw new IllegalArgumentException("amount scale must not exceed 18");
        }
    }

    public static Money zero(Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        return new Money(BigDecimal.ZERO.setScale(currency.minorUnits()), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier must not be null");
        return new Money(
            amount.multiply(multiplier)
                .setScale(currency.minorUnits(), RoundingMode.HALF_EVEN),
            currency
        );
    }

    public Money rounded() {
        return new Money(amount.setScale(currency.minorUnits(), RoundingMode.HALF_EVEN), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (currency != other.currency) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
