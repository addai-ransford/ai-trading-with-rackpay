package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletCreditService {
    private final WalletFinancialOperationService operations;

    public WalletCreditService(WalletFinancialOperationService operations) {
        this.operations = operations;
    }

    public FinancialTransactionEntity credit(IdempotencyKey idempotencyKey,
                                             String requestHash,
                                             UUID walletId,
                                             UUID fundingAccountId,
                                             Money amount) {
        return operations.creditWallet(
            idempotencyKey, requestHash, walletId, fundingAccountId, amount
        );
    }
}
