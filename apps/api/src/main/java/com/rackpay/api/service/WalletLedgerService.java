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
    private final WalletBalanceJpaRepository balances;
    private final LedgerAccountJpaRepository accounts;
    private final LedgerTransactionJpaRepository ledgerTransactions;

    public WalletLedgerService(WalletJpaRepository wallets, WalletBalanceJpaRepository balances,
                               LedgerAccountJpaRepository accounts, LedgerTransactionJpaRepository ledgerTransactions) {
        this.wallets = wallets;
        this.balances = balances;
        this.accounts = accounts;
        this.ledgerTransactions = ledgerTransactions;
    }

    @Transactional
    public void postWalletDebit(UUID walletId, UUID counterpartyAccountId, Money amount) {
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("transfer amount must be positive");
        }

        wallets.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("wallet not found"));

        WalletBalanceEntity balance = balances.findByWalletIdAndCurrencyForUpdate(walletId, amount.currency())
            .orElseThrow(() -> new IllegalArgumentException(
                "wallet has no " + amount.currency() + " balance"
            ));

        LedgerAccountEntity walletAccount = accounts.findById(balance.getLedgerAccountId())
            .orElseThrow(() -> new IllegalArgumentException("wallet ledger account not found"));
        LedgerAccountEntity counterpartyAccount = accounts.findById(counterpartyAccountId)
            .orElseThrow(() -> new IllegalArgumentException("counterparty ledger account not found"));

        if (walletAccount.getCurrency() != amount.currency()
            || counterpartyAccount.getCurrency() != amount.currency()) {
            throw new IllegalArgumentException("currency mismatch");
        }
        if (balance.getBalance().compareTo(amount.amount()) < 0) {
            throw new IllegalStateException("insufficient wallet funds");
        }

        Instant now = Instant.now();
        balance.setBalance(balance.getBalance().subtract(amount.amount()));

        LedgerTransactionEntity transaction = new LedgerTransactionEntity(UUID.randomUUID(), now);

        // Customer stored-value funds are a platform liability. Decreasing a liability is a DEBIT.
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), walletAccount, amount.amount(), amount.currency(),
            EntryDirection.DEBIT, now
        ));
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), counterpartyAccount, amount.amount(), amount.currency(),
            EntryDirection.CREDIT, now
        ));

        ledgerTransactions.save(transaction);
    }
}
