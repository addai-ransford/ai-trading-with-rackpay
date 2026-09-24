package com.rackpay.api.service;

import com.rackpay.api.domain.ledger.*;
import com.rackpay.api.domain.money.Money;
import com.rackpay.api.persistence.ledger.*;
import com.rackpay.api.persistence.wallet.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    public void transferFromWalletToLedger(UUID walletId, UUID ledgerAccountId, Money amount) {
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("transfer amount must be positive");
        }

        WalletEntity wallet = wallets.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("wallet not found"));

        LedgerAccountEntity account = accounts.findById(ledgerAccountId)
            .orElseThrow(() -> new IllegalArgumentException("ledger account not found"));

        if (wallet.getCurrency() != amount.currency() || account.getCurrency() != amount.currency()) {
            throw new IllegalArgumentException("currency mismatch");
        }

        if (wallet.getBalance().compareTo(amount.amount()) < 0) {
            throw new IllegalStateException("insufficient wallet funds");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount.amount()));

        LedgerTransactionEntity transaction =
            new LedgerTransactionEntity(UUID.randomUUID(), Instant.now());

        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), account, amount.amount(), amount.currency(),
            EntryDirection.DEBIT, Instant.now()
        ));

        ledgerTransactions.save(transaction);
    }
}
