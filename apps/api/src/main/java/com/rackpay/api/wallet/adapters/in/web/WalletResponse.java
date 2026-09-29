package com.rackpay.api.wallet.adapters.in.web;

import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.wallet.core.model.Wallet;

import java.util.List;
import java.util.UUID;

public record WalletResponse(
    UUID walletId,
    UUID ownerId,
    List<WalletBalanceResponse> balances
) {}
