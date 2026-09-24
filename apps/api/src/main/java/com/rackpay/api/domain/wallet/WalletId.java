package com.rackpay.api.domain.wallet;

import java.util.UUID;

public record WalletId(UUID value) {
    public WalletId {
        if (value == null) throw new IllegalArgumentException("wallet id must not be null");
    }

    public static WalletId newId() {
        return new WalletId(UUID.randomUUID());
    }
}
