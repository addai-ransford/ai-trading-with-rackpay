package com.rackpay.api.service;

import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.domain.transaction.TransactionStatus;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;
import com.rackpay.api.persistence.transaction.FinancialTransactionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class FinancialTransactionService {
    private final FinancialTransactionJpaRepository transactions;

    public FinancialTransactionService(FinancialTransactionJpaRepository transactions) {
        this.transactions = transactions;
    }

    /**
     * Joins the caller's transaction deliberately. The idempotency record must commit
     * atomically with the wallet balance and ledger mutation.
     */
    @Transactional
    public FinancialTransactionEntity startOrGet(IdempotencyKey key, String requestHash) {
        validateRequestHash(requestHash);
        transactions.lockIdempotencyKey(key.value());

        FinancialTransactionEntity existing =
            transactions.findByIdempotencyKeyForUpdate(key.value()).orElse(null);

        if (existing != null) {
            if (!existing.getRequestHash().equalsIgnoreCase(requestHash)) {
                throw new IllegalArgumentException(
                    "idempotency key was already used for a different request"
                );
            }
            return existing;
        }

        Instant now = Instant.now();
        return transactions.save(new FinancialTransactionEntity(
            UUID.randomUUID(),
            key.value(),
            requestHash.toLowerCase(),
            TransactionStatus.PENDING,
            now,
            now
        ));
    }

    public void markProcessing(FinancialTransactionEntity transaction) {
        transaction.markProcessing(Instant.now());
    }

    public void attachWalletOperation(FinancialTransactionEntity transaction,
                                       UUID walletId,
                                       FinancialTransactionEntity.OperationType operationType,
                                       java.math.BigDecimal amount,
                                       com.rackpay.api.domain.money.Currency currency,
                                       UUID ledgerTransactionId) {
        transaction.attachWalletOperation(
            walletId, operationType, amount, currency, ledgerTransactionId
        );
    }

    public void markCompleted(FinancialTransactionEntity transaction) {
        transaction.markCompleted(Instant.now());
    }

    @Transactional
    public void markFailed(UUID transactionId) {
        FinancialTransactionEntity transaction = transactions.findByIdForUpdate(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("financial transaction not found"));
        transaction.markFailed(Instant.now());
    }

    private void validateRequestHash(String requestHash) {
        if (requestHash == null || !requestHash.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("request hash must be a SHA-256 hexadecimal value");
        }
    }
}
