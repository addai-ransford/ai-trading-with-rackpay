package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.domain.transaction.TransactionStatus;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletFinancialOperationService {
    private final FinancialTransactionService financialTransactions;
    private final WalletLedgerService walletLedger;

    public WalletFinancialOperationService(FinancialTransactionService financialTransactions,
                                           WalletLedgerService walletLedger) {
        this.financialTransactions = financialTransactions;
        this.walletLedger = walletLedger;
    }

    @Transactional
    public FinancialTransactionEntity debitWallet(IdempotencyKey idempotencyKey, String requestHash,
                                                  UUID walletId, UUID counterpartyAccountId, Money amount) {
        return execute(
            idempotencyKey,
            requestHash,
            walletId,
            counterpartyAccountId,
            amount,
            FinancialTransactionEntity.OperationType.DEBIT
        );
    }

    @Transactional
    public FinancialTransactionEntity creditWallet(IdempotencyKey idempotencyKey, String requestHash,
                                                   UUID walletId, UUID counterpartyAccountId, Money amount) {
        return execute(
            idempotencyKey,
            requestHash,
            walletId,
            counterpartyAccountId,
            amount,
            FinancialTransactionEntity.OperationType.CREDIT
        );
    }

    private FinancialTransactionEntity execute(
        IdempotencyKey idempotencyKey,
        String requestHash,
        UUID walletId,
        UUID counterpartyAccountId,
        Money amount,
        FinancialTransactionEntity.OperationType operationType
    ) {
        FinancialTransactionEntity transaction =
            financialTransactions.startOrGet(idempotencyKey, requestHash);

        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return transaction;
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                "financial transaction is already " + transaction.getStatus()
            );
        }

        financialTransactions.markProcessing(transaction);

        UUID ledgerTransactionId = operationType == FinancialTransactionEntity.OperationType.CREDIT
            ? walletLedger.postWalletCredit(walletId, counterpartyAccountId, amount)
            : walletLedger.postWalletDebit(walletId, counterpartyAccountId, amount);

        financialTransactions.attachWalletOperation(
            transaction,
            walletId,
            operationType,
            amount.amount(),
            amount.currency(),
            ledgerTransactionId
        );
        financialTransactions.markCompleted(transaction);

        return transaction;
    }
}
