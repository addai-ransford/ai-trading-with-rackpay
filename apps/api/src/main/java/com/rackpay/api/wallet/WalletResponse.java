package com.rackpay.api.wallet;

import java.util.List;
import java.util.UUID;

public record WalletResponse(
    UUID walletId,
    UUID ownerId,
    List<WalletBalanceResponse> balances
) {}
