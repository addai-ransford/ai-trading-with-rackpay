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

    @Test
    void usesZeroMinorUnitsForXofAndUgx() {
        assertEquals(0, Currency.XOF.minorUnits());
        assertEquals(0, Currency.UGX.minorUnits());
        assertEquals(2, Currency.EUR.minorUnits());
    }

    @Test
    void roundsMultiplicationToCurrencyMinorUnit() {
        var eur = new Money(new BigDecimal("10.00"), Currency.EUR)
            .multiply(new BigDecimal("0.333"));

        var xof = new Money(new BigDecimal("10"), Currency.XOF)
            .multiply(new BigDecimal("0.333"));

        assertEquals(0, eur.amount().compareTo(new BigDecimal("3.33")));
        assertEquals(0, xof.amount().compareTo(new BigDecimal("3")));
    }

    @Test
    void roundedNormalizesExistingAmountToCurrencyMinorUnit() {
        var money = new Money(new BigDecimal("10.126"), Currency.EUR).rounded();

        assertEquals(0, money.amount().compareTo(new BigDecimal("10.13")));
    }
}
