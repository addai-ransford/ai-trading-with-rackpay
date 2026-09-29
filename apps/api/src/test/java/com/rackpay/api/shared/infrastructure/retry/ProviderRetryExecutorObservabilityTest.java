package com.rackpay.api.shared.infrastructure.retry;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderRetryExecutorObservabilityTest {

    @Test
    void recordsRetryAndSuccessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(2), 2.0, 0.0),
            registry
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(
            "PAYSTACK",
            "payout-status",
            () -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("transient");
                }
                return "COMPLETED";
            },
            failure -> true
        );

        assertEquals("COMPLETED", result);
        assertEquals(1.0, registry.counter(
            "rackpay.provider.retry.attempts",
            "provider", "PAYSTACK",
            "operation", "payout-status",
            "outcome", "retrying"
        ).count());
        assertEquals(1.0, registry.counter(
            "rackpay.provider.retry.attempts",
            "provider", "PAYSTACK",
            "operation", "payout-status",
            "outcome", "success"
        ).count());
    }

    @Test
    void recordsExhaustionMetricWhenRetriesAreConsumed() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new RetryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(1), 1.0, 0.0),
            registry
        );

        assertThrows(IllegalStateException.class, () -> executor.execute(
            "FLUTTERWAVE",
            "payout-reconciliation",
            () -> { throw new IllegalStateException("transient"); },
            failure -> true
        ));

        assertEquals(1.0, registry.counter(
            "rackpay.provider.retry.attempts",
            "provider", "FLUTTERWAVE",
            "operation", "payout-reconciliation",
            "outcome", "exhausted"
        ).count());
    }
}
