package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import com.rackpay.api.persistence.ledger.LedgerTransactionEntity;
import com.rackpay.api.persistence.ledger.LedgerTransactionJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittancePayoutLedgerPostingEntity;
import com.rackpay.api.persistence.remittance.RemittancePayoutLedgerPostingJpaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemittancePayoutLedgerServiceTest {

    @Test
    void completedPayoutPostsClearingDebitAndSettlementCreditOnce() {
        LedgerAccountJpaRepository accounts = mock(LedgerAccountJpaRepository.class);
        LedgerTransactionJpaRepository transactions = mock(LedgerTransactionJpaRepository.class);
        RemittancePayoutLedgerPostingJpaRepository postings = mock(RemittancePayoutLedgerPostingJpaRepository.class);

        UUID clearingId = UUID.randomUUID();
        LedgerAccountEntity clearing = new LedgerAccountEntity(
            clearingId, "RackPay Remittance Clearing EUR", Currency.EUR,
            com.rackpay.api.domain.ledger.LedgerAccountType.LIABILITY, Instant.now()
        );

        when(accounts.findById(clearingId)).thenReturn(Optional.of(clearing));
        when(accounts.findByNameAndCurrency("RackPay Payout Settlement FLUTTERWAVE EUR", Currency.EUR))
            .thenReturn(Optional.empty());
        when(postings.findByRemittanceId(any())).thenReturn(Optional.empty());
        when(transactions.save(any(LedgerTransactionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(postings.save(any(RemittancePayoutLedgerPostingEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RemittanceEntity remittance = mock(RemittanceEntity.class);
        UUID remittanceId = UUID.randomUUID();
        when(remittance.getId()).thenReturn(remittanceId);
        when(remittance.getClearingAccountId()).thenReturn(clearingId);
        when(remittance.getSourceCurrency()).thenReturn(Currency.EUR);
        when(remittance.getSourceAmount()).thenReturn(new BigDecimal("100.00"));
        when(remittance.getFeeAmount()).thenReturn(new BigDecimal("2.50"));

        RemittancePayoutLedgerService service =
            new RemittancePayoutLedgerService(accounts, transactions, postings);

        service.recordCompletedPayout(remittance, "FLUTTERWAVE", "fw-123");

        var txCaptor = org.mockito.ArgumentCaptor.forClass(LedgerTransactionEntity.class);
        verify(transactions).save(txCaptor.capture());

        LedgerTransactionEntity tx = txCaptor.getValue();
        assertEquals(2, tx.getEntries().size());
        assertEquals(com.rackpay.api.domain.ledger.EntryDirection.DEBIT,
            tx.getEntries().get(0).getDirection());
        assertEquals(clearingId, tx.getEntries().get(0).getAccount().getId());
        assertEquals(com.rackpay.api.domain.ledger.EntryDirection.CREDIT,
            tx.getEntries().get(1).getDirection());
        assertEquals(new BigDecimal("102.50"), tx.getEntries().get(0).getAmount());
        assertEquals(new BigDecimal("102.50"), tx.getEntries().get(1).getAmount());

        verify(postings).save(any(RemittancePayoutLedgerPostingEntity.class));
    }

    @Test
    void existingPostingPreventsDuplicateLedgerMovement() {
        LedgerAccountJpaRepository accounts = mock(LedgerAccountJpaRepository.class);
        LedgerTransactionJpaRepository transactions = mock(LedgerTransactionJpaRepository.class);
        RemittancePayoutLedgerPostingJpaRepository postings = mock(RemittancePayoutLedgerPostingJpaRepository.class);

        UUID remittanceId = UUID.randomUUID();
        when(postings.findByRemittanceId(remittanceId))
            .thenReturn(Optional.of(mock(RemittancePayoutLedgerPostingEntity.class)));

        RemittanceEntity remittance = mock(RemittanceEntity.class);
        when(remittance.getId()).thenReturn(remittanceId);

        RemittancePayoutLedgerService service =
            new RemittancePayoutLedgerService(accounts, transactions, postings);

        service.recordCompletedPayout(remittance, "PAYSTACK", "ps-123");

        verifyNoInteractions(accounts, transactions);
        verify(postings, never()).save(any());
    }
}
