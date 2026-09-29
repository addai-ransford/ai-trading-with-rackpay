package com.rackpay.api.shared.infrastructure.retry;

import java.time.Duration;

public record RetryPolicy(
    int maxAttempts,
    Duration initialBackoff,
    Duration maxBackoff,
    double multiplier,
    double jitter
) {
    public RetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        }
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier must be >= 1");
        if (jitter < 0.0 || jitter > 1.0) throw new IllegalArgumentException("jitter must be between 0 and 1");
    }

    public Duration backoffFor(int failedAttempt) {
        if (failedAttempt < 1) return Duration.ZERO;
        double exponential = initialBackoff.toMillis()
            * Math.pow(multiplier, failedAttempt - 1);
        long bounded = Math.min(maxBackoff.toMillis(), Math.max(1L, Math.round(exponential)));
        return Duration.ofMillis(bounded);
    }
}
