package com.rackpay.api.shared.infrastructure.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ProviderRetryExecutorTest {

    @Test
    void retriesTransientFailuresUntilSuccess() {
        ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(2), 2.0, 0.0)
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(
            () -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary");
                }
                return "ok";
            },
            failure -> failure instanceof IllegalStateException
        );

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void stopsAfterMaximumAttempts() {
        ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new RetryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(2), 2.0, 0.0)
        );
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
            executor.execute(
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("temporary");
                },
                failure -> true
            )
        );

        assertEquals(2, attempts.get());
    }

    @Test
    void doesNotRetryPermanentFailure() {
        ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(2), 2.0, 0.0)
        );
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () ->
            executor.execute(
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("permanent");
                },
                failure -> false
            )
        );

        assertEquals(1, attempts.get());
    }
}
