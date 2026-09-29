package com.rackpay.api.wallet.core.service;

import com.rackpay.api.ledger.core.model.LedgerAccount;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.wallet.core.model.Wallet;
import com.rackpay.api.wallet.core.model.WalletId;

import com.rackpay.api.ledger.core.model.LedgerAccountType;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountJpaRepository;
import com.rackpay.api.wallet.adapters.out.persistence.WalletBalanceEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletBalanceJpaRepository;
import com.rackpay.api.wallet.adapters.out.persistence.WalletEntity;
import com.rackpay.api.wallet.adapters.out.persistence.WalletJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletBalanceService {
    private final WalletJpaRepository wallets;
    private final WalletBalanceJpaRepository balances;
    private final LedgerAccountJpaRepository ledgerAccounts;

    public WalletBalanceService(
        WalletJpaRepository wallets,
        WalletBalanceJpaRepository balances,
        LedgerAccountJpaRepository ledgerAccounts
    ) {
        this.wallets = wallets;
        this.balances = balances;
        this.ledgerAccounts = ledgerAccounts;
    }

    @Transactional
    public WalletBalanceEntity openBalance(UUID walletId, Currency currency) {
        WalletEntity wallet = wallets.findByIdForUpdate(walletId)
            .orElseThrow(() -> new IllegalArgumentException("wallet not found"));

        return balances.findByWalletIdAndCurrency(wallet.getId(), currency)
            .orElseGet(() -> createBalance(wallet.getId(), currency));
    }

    private WalletBalanceEntity createBalance(UUID walletId, Currency currency) {
        Instant now = Instant.now();
        UUID ledgerAccountId = UUID.randomUUID();
        UUID balanceId = UUID.randomUUID();

        ledgerAccounts.saveAndFlush(new LedgerAccountEntity(
            ledgerAccountId,
            "Wallet " + walletId + " " + currency.name(),
            currency,
            LedgerAccountType.LIABILITY,
            now
        ));

        return balances.saveAndFlush(new WalletBalanceEntity(
            balanceId,
            walletId,
            currency,
            BigDecimal.ZERO,
            ledgerAccountId,
            now
        ));
    }
}
