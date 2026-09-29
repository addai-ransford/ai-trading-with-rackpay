package com.rackpay.api.wallet.core.service;

import com.rackpay.api.shared.core.transaction.FinancialTransaction;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Money;
import com.rackpay.api.shared.core.transaction.IdempotencyKey;
import com.rackpay.api.transaction.adapters.out.persistence.FinancialTransactionEntity;
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
