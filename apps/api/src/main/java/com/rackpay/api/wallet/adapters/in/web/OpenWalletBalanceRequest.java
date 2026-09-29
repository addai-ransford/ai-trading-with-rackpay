package com.rackpay.api.wallet.adapters.in.web;

import com.rackpay.api.wallet.core.model.Wallet;

import com.rackpay.api.shared.core.money.Currency;
import jakarta.validation.constraints.NotNull;

public record OpenWalletBalanceRequest(
    @NotNull Currency currency
) {}
