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
    public void postWalletDebit(UUID walletId, UUID walletLedgerAccountId,
                                UUID counterpartyAccountId, Money amount) {
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("transfer amount must be positive");
        }

        WalletEntity wallet = wallets.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("wallet not found"));

        LedgerAccountEntity walletAccount = accounts.findById(walletLedgerAccountId)
            .orElseThrow(() -> new IllegalArgumentException("wallet ledger account not found"));

        LedgerAccountEntity counterpartyAccount = accounts.findById(counterpartyAccountId)
            .orElseThrow(() -> new IllegalArgumentException("counterparty ledger account not found"));

        if (wallet.getCurrency() != amount.currency()
            || walletAccount.getCurrency() != amount.currency()
            || counterpartyAccount.getCurrency() != amount.currency()) {
            throw new IllegalArgumentException("currency mismatch");
        }

        if (wallet.getBalance().compareTo(amount.amount()) < 0) {
            throw new IllegalStateException("insufficient wallet funds");
        }

        Instant now = Instant.now();
        wallet.setBalance(wallet.getBalance().subtract(amount.amount()));

        LedgerTransactionEntity transaction =
            new LedgerTransactionEntity(UUID.randomUUID(), now);

        // A wallet is an asset. Decreasing an asset is a CREDIT.
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), walletAccount, amount.amount(), amount.currency(),
            EntryDirection.CREDIT, now
        ));

        // The receiving/counterparty account receives the corresponding DEBIT.
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), counterpartyAccount, amount.amount(), amount.currency(),
            EntryDirection.DEBIT, now
        ));

        ledgerTransactions.save(transaction);
    }
}
