package com.rackpay.api.domain.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void addsAmountsInSameCurrency() {
        var result = new Money(new BigDecimal("10.00"), Currency.EUR)
            .add(new Money(new BigDecimal("5.50"), Currency.EUR));

        assertEquals(0, result.amount().compareTo(new BigDecimal("15.50")));
    }

    @Test
    void rejectsCurrencyMismatch() {
        var eur = new Money(new BigDecimal("10.00"), Currency.EUR);
        var usd = new Money(new BigDecimal("5.00"), Currency.USD);

        assertThrows(IllegalArgumentException.class, () -> eur.add(usd));
    }
}
