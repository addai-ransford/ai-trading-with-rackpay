package com.rackpay.api.service;

import com.rackpay.api.domain.ledger.EntryDirection;
import com.rackpay.api.domain.money.Money;
import com.rackpay.api.persistence.ledger.*;
import com.rackpay.api.persistence.wallet.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WalletLedgerService {
    private final WalletJpaRepository wallets;
    private final LedgerAccountJpaRepository accounts;
    private final LedgerTransactionJpaRepository ledgerTransactions;

    public WalletLedgerService(WalletJpaRepository wallets,
                               LedgerAccountJpaRepository accounts,
                               LedgerTransactionJpaRepository ledgerTransactions) {
        this.wallets = wallets;
        this.accounts = accounts;
        this.ledgerTransactions = ledgerTransactions;
    }

    @Transactional
    public void postWalletTransfer(UUID walletId, UUID debitAccountId, UUID creditAccountId, Money amount) {
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("transfer amount must be positive");
        }

        WalletEntity wallet = wallets.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("wallet not found"));
        LedgerAccountEntity debitAccount = accounts.findById(debitAccountId)
            .orElseThrow(() -> new IllegalArgumentException("debit ledger account not found"));
        LedgerAccountEntity creditAccount = accounts.findById(creditAccountId)
            .orElseThrow(() -> new IllegalArgumentException("credit ledger account not found"));

        if (wallet.getCurrency() != amount.currency()
            || debitAccount.getCurrency() != amount.currency()
            || creditAccount.getCurrency() != amount.currency()) {
            throw new IllegalArgumentException("currency mismatch");
        }

        if (wallet.getBalance().compareTo(amount.amount()) < 0) {
            throw new IllegalStateException("insufficient wallet funds");
        }

        Instant now = Instant.now();
        wallet.setBalance(wallet.getBalance().subtract(amount.amount()));

        LedgerTransactionEntity transaction =
            new LedgerTransactionEntity(UUID.randomUUID(), now);

        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), debitAccount, amount.amount(), amount.currency(),
            EntryDirection.DEBIT, now
        ));
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), creditAccount, amount.amount(), amount.currency(),
            EntryDirection.CREDIT, now
        ));

        ledgerTransactions.save(transaction);
    }
}
