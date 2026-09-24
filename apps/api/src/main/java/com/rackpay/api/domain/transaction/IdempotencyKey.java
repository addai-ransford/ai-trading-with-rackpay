package com.rackpay.api.domain.transaction;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "idempotency key must not be null");
        if (value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("idempotency key must contain 1-255 characters");
        }
    }
}
