package com.rackpay.api.wallet.adapters.in.web;

import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.wallet.adapters.out.persistence.WalletBalanceEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletBalanceResponse(
    UUID balanceId,
    Currency currency,
    BigDecimal balance
) {
    public static WalletBalanceResponse from(WalletBalanceEntity entity) {
        return new WalletBalanceResponse(
            entity.getId(),
            entity.getCurrency(),
            entity.getBalance()
        );
    }
}
