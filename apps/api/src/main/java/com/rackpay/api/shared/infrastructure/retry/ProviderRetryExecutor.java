package com.rackpay.api.shared.infrastructure.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderRetryExecutor {
    private final RetryPolicy policy;

    @Autowired
    public ProviderRetryExecutor(
        @Value("${rackpay.providers.retry.max-attempts:3}") int maxAttempts,
        @Value("${rackpay.providers.retry.initial-backoff:500ms}") Duration initialBackoff,
        @Value("${rackpay.providers.retry.max-backoff:5s}") Duration maxBackoff,
        @Value("${rackpay.providers.retry.multiplier:2.0}") double multiplier,
        @Value("${rackpay.providers.retry.jitter:0.20}") double jitter
    ) {
        this(new RetryPolicy(maxAttempts, initialBackoff, maxBackoff, multiplier, jitter));
    }

    public ProviderRetryExecutor(RetryPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public <T> T execute(Supplier<T> operation, Predicate<Throwable> retryable) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(retryable);

        Throwable last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return operation.get();
            } catch (Throwable failure) {
                last = failure;
                if (attempt == policy.maxAttempts() || !retryable.test(failure)) {
                    throw propagate(failure);
                }
                sleep(withJitter(policy.backoffFor(attempt)));
            }
        }
        throw propagate(last);
    }

    public void execute(Runnable operation, Predicate<Throwable> retryable) {
        execute(() -> {
            operation.run();
            return null;
        }, retryable);
    }

    private Duration withJitter(Duration base) {
        if (base.isZero() || policy.jitter() == 0.0) return base;
        double factor = 1.0 + ThreadLocalRandom.current()
            .nextDouble(-policy.jitter(), policy.jitter());
        return Duration.ofMillis(Math.max(1L, Math.round(base.toMillis() * factor)));
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider retry interrupted", e);
        }
    }

    private RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Provider operation failed", failure);
    }
}
