package com.rackpay.api.shared.infrastructure.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderRetryExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProviderRetryExecutor.class);
    private final RetryPolicy policy;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ProviderRetryExecutor(
        @Value("${rackpay.providers.retry.max-attempts:3}") int maxAttempts,
        @Value("${rackpay.providers.retry.initial-backoff:500ms}") Duration initialBackoff,
        @Value("${rackpay.providers.retry.max-backoff:5s}") Duration maxBackoff,
        @Value("${rackpay.providers.retry.multiplier:2.0}") double multiplier,
        @Value("${rackpay.providers.retry.jitter:0.20}") double jitter,
        MeterRegistry meterRegistry
    ) {
        this(new RetryPolicy(maxAttempts, initialBackoff, maxBackoff, multiplier, jitter), meterRegistry);
    }

    public ProviderRetryExecutor(RetryPolicy policy) {
        this(policy, null);
    }

    public ProviderRetryExecutor(RetryPolicy policy, MeterRegistry meterRegistry) {
        this.policy = Objects.requireNonNull(policy);
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(Supplier<T> operation, Predicate<Throwable> retryable) {
        return execute("unknown", "provider-operation", operation, retryable);
    }

    public <T> T execute(
        String provider,
        String operation,
        Supplier<T> supplier,
        Predicate<Throwable> retryable
    ) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(retryable);

        Objects.requireNonNull(provider);
        Objects.requireNonNull(operation);
        Throwable last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                T result = supplier.get();
                recordAttempt(provider, operation, attempt, "success");
                return result;
            } catch (Throwable failure) {
                last = failure;
                boolean shouldRetry = retryable.test(failure);
                String outcome = attempt == policy.maxAttempts()
                    ? "exhausted"
                    : (shouldRetry ? "retrying" : "non_retryable");
                recordAttempt(provider, operation, attempt, outcome);
                if (attempt == policy.maxAttempts() || !shouldRetry) {
                    log.error(
                        "provider_operation_failed provider={} operation={} attempts={} exception={} correlation_id={}",
                        provider, operation, attempt, failure.getClass().getSimpleName(),
                        MDC.get("correlationId"), failure
                    );
                    throw propagate(failure);
                }
                log.warn(
                    "provider_operation_retry provider={} operation={} attempt={} next_backoff={}ms exception={} correlation_id={}",
                    provider, operation, attempt, withJitter(policy.backoffFor(attempt)).toMillis(),
                    failure.getClass().getSimpleName(), MDC.get("correlationId")
                );
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

    private void recordAttempt(String provider, String operation, int attempt, String outcome) {
        if (meterRegistry == null) return;
        meterRegistry.counter(
            "rackpay.provider.retry.attempts",
            "provider", provider,
            "operation", operation,
            "outcome", outcome
        ).increment();
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
