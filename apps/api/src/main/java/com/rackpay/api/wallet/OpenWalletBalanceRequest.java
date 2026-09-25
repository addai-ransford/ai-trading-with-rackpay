package com.rackpay.api.wallet;

import com.rackpay.api.domain.money.Currency;
import jakarta.validation.constraints.NotNull;

public record OpenWalletBalanceRequest(
    @NotNull Currency currency
) {}
