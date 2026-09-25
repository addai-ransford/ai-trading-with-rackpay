package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletDebitService {
    private final WalletFinancialOperationService operations;

    public WalletDebitService(WalletFinancialOperationService operations) {
        this.operations = operations;
    }

    public FinancialTransactionEntity debit(IdempotencyKey idempotencyKey,
                                             String requestHash,
                                             UUID walletId,
                                             UUID destinationAccountId,
                                             Money amount) {
        return operations.debitWallet(
            idempotencyKey, requestHash, walletId, destinationAccountId, amount
        );
    }
}
