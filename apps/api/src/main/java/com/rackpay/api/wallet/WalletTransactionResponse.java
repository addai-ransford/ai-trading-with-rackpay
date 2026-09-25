package com.rackpay.api.wallet;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.transaction.TransactionStatus;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
    UUID transactionId,
    FinancialTransactionEntity.OperationType operationType,
    BigDecimal amount,
    Currency currency,
    TransactionStatus status,
    UUID ledgerTransactionId,
    Instant createdAt,
    Instant updatedAt
) {
    public static WalletTransactionResponse from(FinancialTransactionEntity entity) {
        return new WalletTransactionResponse(
            entity.getId(),
            entity.getOperationType(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getLedgerTransactionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
