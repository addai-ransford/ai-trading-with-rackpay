package com.rackpay.api.ledger.core.model;

import java.util.UUID;

public record LedgerAccountId(UUID value) {
    public LedgerAccountId {
        if (value == null) throw new IllegalArgumentException("ledger account id must not be null");
    }

    public static LedgerAccountId newId() {
        return new LedgerAccountId(UUID.randomUUID());
    }
}
