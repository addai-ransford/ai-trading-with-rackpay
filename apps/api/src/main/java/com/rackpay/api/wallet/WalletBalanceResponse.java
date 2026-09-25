package com.rackpay.api.wallet;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.wallet.WalletBalanceEntity;

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
