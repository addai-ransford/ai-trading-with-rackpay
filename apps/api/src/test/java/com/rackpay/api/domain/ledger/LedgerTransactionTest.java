package com.rackpay.api.domain.ledger;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerTransactionTest {
    @Test
    void acceptsBalancedDoubleEntry() {
        var amount = new Money(new BigDecimal("100.00"), Currency.EUR);
        var transaction = LedgerTransaction.create(List.of(
            new LedgerEntry(LedgerAccountId.newId(), amount, EntryDirection.DEBIT),
            new LedgerEntry(LedgerAccountId.newId(), amount, EntryDirection.CREDIT)
        ));

        assertEquals(2, transaction.entries().size());
    }

    @Test
    void rejectsUnbalancedEntries() {
        var debit = new Money(new BigDecimal("100.00"), Currency.EUR);
        var credit = new Money(new BigDecimal("99.99"), Currency.EUR);

        assertThrows(IllegalArgumentException.class, () ->
            LedgerTransaction.create(List.of(
                new LedgerEntry(LedgerAccountId.newId(), debit, EntryDirection.DEBIT),
                new LedgerEntry(LedgerAccountId.newId(), credit, EntryDirection.CREDIT)
            )));
    }
}
