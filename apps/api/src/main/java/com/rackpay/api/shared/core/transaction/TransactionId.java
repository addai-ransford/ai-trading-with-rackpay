package com.rackpay.api.shared.core.transaction;

import java.util.UUID;

public record TransactionId(UUID value) {
    public TransactionId {
        if (value == null) throw new IllegalArgumentException("transaction id must not be null");
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }
}
