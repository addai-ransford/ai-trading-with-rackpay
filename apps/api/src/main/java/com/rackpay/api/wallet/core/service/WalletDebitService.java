package com.rackpay.api.wallet.core.service;

import com.rackpay.api.shared.core.transaction.FinancialTransaction;
import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Money;
import com.rackpay.api.shared.core.transaction.IdempotencyKey;
import com.rackpay.api.transaction.adapters.out.persistence.FinancialTransactionEntity;
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
