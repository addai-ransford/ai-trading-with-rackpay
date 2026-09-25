package com.rackpay.api.service;

import com.rackpay.api.domain.ledger.LedgerAccountType;
import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import com.rackpay.api.persistence.wallet.WalletBalanceEntity;
import com.rackpay.api.persistence.wallet.WalletBalanceJpaRepository;
import com.rackpay.api.persistence.wallet.WalletEntity;
import com.rackpay.api.persistence.wallet.WalletJpaRepository;
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
