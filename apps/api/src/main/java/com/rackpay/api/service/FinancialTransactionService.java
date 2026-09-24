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
        FinancialTransactionEntity transaction = new FinancialTransactionEntity(
            UUID.randomUUID(),
            key.value(),
            requestHash.toLowerCase(),
            TransactionStatus.PENDING,
            now,
            now
        );

        return transactions.save(transaction);
    }

    @Transactional
    public void markProcessing(UUID transactionId) {
        FinancialTransactionEntity transaction = getForUpdate(transactionId);
        transaction.markProcessing(Instant.now());
    }

    @Transactional
    public void markCompleted(UUID transactionId) {
        FinancialTransactionEntity transaction = getForUpdate(transactionId);
        transaction.markCompleted(Instant.now());
    }

    @Transactional
    public void markFailed(UUID transactionId) {
        FinancialTransactionEntity transaction = getForUpdate(transactionId);
        transaction.markFailed(Instant.now());
    }

    private FinancialTransactionEntity getForUpdate(UUID transactionId) {
        return transactions.findByIdForUpdate(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("financial transaction not found"));
    }

    private void validateRequestHash(String requestHash) {
        if (requestHash == null || !requestHash.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("request hash must be a SHA-256 hexadecimal value");
        }
    }
}
