package com.rackpay.api.wallet.adapters.in.web;

import com.rackpay.api.ledger.core.model.LedgerTransaction;
import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.shared.core.transaction.FinancialTransaction;
import com.rackpay.api.shared.core.transaction.TransactionId;
import com.rackpay.api.transaction.adapters.out.persistence.OperationType;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.core.transaction.TransactionStatus;
import com.rackpay.api.transaction.adapters.out.persistence.FinancialTransactionEntity;

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
