package com.rackpay.api.remittance.core.service;

import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.remittance.adapters.out.persistence.payout;
import com.rackpay.api.remittance.adapters.out.providers.PreparedPayoutMapper;
import com.rackpay.api.remittance.core.model.MobileMoneyNetwork;
import com.rackpay.api.remittance.core.model.PayoutMethod;
import com.rackpay.api.remittance.core.model.PayoutProviderType;
import com.rackpay.api.remittance.core.model.PreparedPayout;
import com.rackpay.api.remittance.ports.out.CreatePayoutCommand;
import com.rackpay.api.remittance.ports.out.PayoutProvider;
import com.rackpay.api.remittance.ports.out.PayoutResult;
import com.rackpay.api.remittance.ports.out.PayoutStatus;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.core.money.Money;

import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkEntity;
import com.rackpay.api.remittance.adapters.out.persistence.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittancePayoutAttemptEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittancePayoutAttemptJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceRecipientJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class RemittancePayoutService {
    private final RemittanceJpaRepository remittances;
    private final RemittanceRecipientJpaRepository recipients;
    private final MobileMoneyNetworkJpaRepository networks;
    private final RemittancePayoutAttemptJpaRepository attempts;
    private final PayoutProviderRegistry providers;
    private final RemittancePayoutRecoveryService recoveryService;
    private final RemittancePayoutLedgerService payoutLedger;
    private final PayoutProviderType defaultProvider;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate recoveryTransactionTemplate;
    private static final Duration PAYOUT_EXECUTION_CLAIM_TTL = Duration.ofSeconds(30);

    public RemittancePayoutService(
        RemittanceJpaRepository remittances,
        RemittanceRecipientJpaRepository recipients,
        MobileMoneyNetworkJpaRepository networks,
        RemittancePayoutAttemptJpaRepository attempts,
        PayoutProviderRegistry providers,
        RemittancePayoutRecoveryService recoveryService,
        RemittancePayoutLedgerService payoutLedger,
        @Value("$"+"{rackpay.payout.default-provider:FLUTTERWAVE}") PayoutProviderType defaultProvider,
        PlatformTransactionManager transactionManager
    ) {
        this.remittances = remittances;
        this.recipients = recipients;
        this.networks = networks;
        this.attempts = attempts;
        this.providers = providers;
        this.recoveryService = recoveryService;
        this.payoutLedger = payoutLedger;
        this.defaultProvider = defaultProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.recoveryTransactionTemplate = new TransactionTemplate(transactionManager);
        this.recoveryTransactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public PayoutResponse execute(UUID remittanceId) {
        int maxAttempts = PayoutProviderType.values().length + 1;

        for (int i = 0; i < maxAttempts; i++) {
            PreparedPayout payout = transactionTemplate.execute(status -> prepareInternal(remittanceId));
            if (payout == null) throw new IllegalStateException("Unable to prepare remittance payout");

            if (payout.status() == RemittanceEntity.Status.RECOVERY_REQUIRED) {
                RemittancePayoutRecoveryService.RecoveryResponse recovery =
                    recoveryService.recover(payout.remittanceId(), payout.providerTransferId());
                return new PayoutResponse(
                    recovery.remittanceId(),
                    recovery.status(),
                    recovery.providerTransferId(),
                    recovery.recoveryStatus()
                );
            }

            if (isTerminal(payout.status())) {
                return new PayoutResponse(
                    payout.remittanceId(), payout.status(), payout.providerTransferId(), payout.status().name()
                );
            }

            if (!payout.executionClaimed()) return retryResponse(payout);

            PayoutProvider provider = providers.require(payout.provider());

            try {
                PayoutProvider.PayoutResult result;

                if (payout.providerTransferId() != null) {
                    PayoutProvider.PayoutStatus status = provider.getPayout(payout.providerTransferId());
                    result = new PayoutProvider.PayoutResult(payout.providerTransferId(), status.name());
                } else {
                    result = provider.findPayoutByReference(payout.payoutReference());
                    if (result == null) {
                        result = provider.createPayout(new PayoutProvider.CreatePayoutCommand(
                            payout.payoutReference(),
                            payout.destinationAmount(),
                            payout.destinationCurrency(),
                            payout.sourceCurrency(),
                            payout.countryCode(),
                            payout.payoutMethod(),
                            payout.networkCode(),
                            payout.normalizedPhoneNumber(),
                            payout.recipientName()
                        ));
                    }
                }

                if (result == null || result.status() == null) {
                    return markRetryRequired(payout);
                }

                PayoutProvider.PayoutStatus providerStatus = result.status().startsWith("FAILED:")
                    ? PayoutProvider.PayoutStatus.FAILED
                    : mapProviderStatus(result.status());

                if (providerStatus == PayoutProvider.PayoutStatus.FAILED
                    || providerStatus == PayoutProvider.PayoutStatus.CANCELLED) {
                    final String failedProviderTransferId = result.providerTransferId();
                    final PayoutProvider.PayoutStatus failedStatus = providerStatus;
                    PayoutResponse next = transactionTemplate.execute(status ->
                        failAttemptAndPrepareNextInternal(
                            payout,
                            failedProviderTransferId,
                            failedStatus
                        )
                    );
                    if (next == null) throw new IllegalStateException("Unable to advance payout attempt");
                    if (!"NEXT_PROVIDER".equals(next.providerStatus())) {
                        if (RemittanceEntity.Status.RECOVERY_REQUIRED == next.status()) {
                            RemittancePayoutRecoveryService.RecoveryResponse recovery =
                                recoveryService.recover(next.remittanceId(), next.providerTransferId());
                            return new PayoutResponse(
                                recovery.remittanceId(),
                                recovery.status(),
                                recovery.providerTransferId(),
                                "RECOVERY_COMPLETED".equals(recovery.recoveryStatus())
                                    ? "ALL_PROVIDERS_FAILED"
                                    : recovery.recoveryStatus()
                            );
                        }
                        return next;
                    }
                    continue;
                }

                final String completedProviderTransferId = result.providerTransferId();
                final PayoutProvider.PayoutStatus completedStatus = providerStatus;
                return transactionTemplate.execute(status ->
                    recordProviderStatusInternal(
                        payout,
                        completedProviderTransferId,
                        completedStatus
                    )
                );
            } catch (RuntimeException ex) {
                // A transport/API exception is not proof that no transfer was created.
                // Persist UNKNOWN and release the short execution claim so the next execution reconciles first.
                return markRetryRequired(payout);
            }
        }

        throw new IllegalStateException("No payout provider attempt could be completed");
    }

    private PreparedPayout prepareInternal(UUID remittanceId) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (isTerminal(remittance.getStatus())) return terminalSnapshot(remittance);

        RemittanceRecipientEntity recipient = recipients.findById(remittance.getRecipientId())
            .orElseThrow(() -> new IllegalStateException("remittance recipient not found"));
        MobileMoneyNetworkEntity network = networks.findById(recipient.getMobileMoneyNetworkId())
            .orElseThrow(() -> new IllegalStateException("mobile money network not found"));

        var command = commandFor(remittance, recipient, network);
        var history = attempts.findAllByRemittanceIdForUpdate(remittanceId);
        RemittancePayoutAttemptEntity attempt = history.isEmpty() ? null : history.get(0);

        if (attempt != null && remittance.hasActivePayoutExecutionClaim(Instant.now())) {
            return prepared(remittance, attempt, recipient, network, false);
        }

        if (attempt == null) {
            PayoutProvider provider = initialProvider(command);
            attempt = createAttempt(remittance, provider.type(), 1);
        } else if (attempt.getStatus() == RemittancePayoutAttemptEntity.Status.FAILED
            || attempt.getStatus() == RemittancePayoutAttemptEntity.Status.CANCELLED) {
            Set<PayoutProviderType> attemptedProviders = attemptedProviders(history);
            PayoutProvider next = providers.nextEligible(
                attempt.getProvider(), attemptedProviders, command
            );
            if (next == null) {
                PayoutResponse response = releaseAfterAllProvidersFailed(remittance, attempt.getProvider(), null);
                return new PreparedPayoutMapper().toPreparedPayout(remittance, attempt.getProvider(), response);
            }
            attempt = createAttempt(remittance, next.type(), attempt.getAttemptNumber() + 1);
        }

        Instant now = Instant.now();
        if (remittance.getStatus() == RemittanceEntity.Status.FUNDS_RESERVED
            || !attempt.getProvider().name().equals(remittance.getPayoutProvider())
            || !attempt.getPayoutReference().equals(remittance.getPayoutReference())) {
            remittance.beginPayoutAttempt(attempt.getProvider().name(), attempt.getPayoutReference(), now);
        }
        remittance.claimPayoutExecution(UUID.randomUUID(), now.plus(PAYOUT_EXECUTION_CLAIM_TTL), now);
        return prepared(remittance, attempt, recipient, network, true);
    }

    private PayoutProvider initialProvider(PayoutProvider.CreatePayoutCommand command) {
        PayoutProvider preferred = providers.require(defaultProvider);
        if (preferred.supportsPayout(command)) return preferred;

        PayoutProvider fallback = providers.nextEligible(
            defaultProvider, Set.of(), command
        );
        if (fallback == null) throw new IllegalStateException("No configured payout provider supports this payout");
        return fallback;
    }

    private RemittancePayoutAttemptEntity createAttempt(
        RemittanceEntity remittance,
        PayoutProviderType provider,
        int number
    ) {
        Instant now = Instant.now();
        String reference = "rp-" + remittance.getId().toString().replace("-", "") + "-" + number;
        RemittancePayoutAttemptEntity attempt = new RemittancePayoutAttemptEntity(
            UUID.randomUUID(), remittance.getId(), number, provider, reference, now
        );
        return attempts.save(attempt);
    }

    private PayoutResponse failAttemptAndPrepareNextInternal(
        PreparedPayout payout,
        String providerTransferId,
        PayoutProvider.PayoutStatus providerStatus
    ) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(payout.remittanceId())
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (!remittance.ownsPayoutExecutionClaim(payout.executionClaimToken())) {
            return currentPayoutResponse(remittance, "STALE_EXECUTION");
        }

        RemittancePayoutAttemptEntity attempt = attempts.findById(payout.attemptId())
            .orElseThrow(() -> new IllegalArgumentException("payout attempt not found"));

        Instant now = Instant.now();
        attempt.record(providerTransferId, mapAttemptStatus(providerStatus), providerStatus.name(), now);
        remittance.clearPayoutExecutionClaim(payout.executionClaimToken(), now);

        RemittanceRecipientEntity recipient = recipients.findById(remittance.getRecipientId())
            .orElseThrow(() -> new IllegalStateException("remittance recipient not found"));
        MobileMoneyNetworkEntity network = networks.findById(recipient.getMobileMoneyNetworkId())
            .orElseThrow(() -> new IllegalStateException("mobile money network not found"));

        var command = commandFor(remittance, recipient, network);
        var history = attempts.findAllByRemittanceIdForUpdate(remittance.getId());
        Set<PayoutProviderType> attemptedProviders = attemptedProviders(history);

        PayoutProvider next = providers.nextEligible(
            attempt.getProvider(), attemptedProviders, command
        );

        if (next == null) {
            return releaseAfterAllProvidersFailed(
                remittance, attempt.getProvider(), providerTransferId
            );
        }

        RemittancePayoutAttemptEntity nextAttempt =
            createAttempt(remittance, next.type(), attempt.getAttemptNumber() + 1);

        remittance.beginPayoutAttempt(
            next.type().name(),
            nextAttempt.getPayoutReference(),
            Instant.now()
        );

        return new PayoutResponse(
            remittance.getId(),
            RemittanceEntity.Status.PAYOUT_PENDING,
            null,
            "NEXT_PROVIDER"
        );
    }

    private PayoutResponse recordProviderStatusInternal(
        PreparedPayout payout,
        String providerTransferId,
        PayoutProvider.PayoutStatus providerStatus
    ) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(payout.remittanceId())
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (!remittance.ownsPayoutExecutionClaim(payout.executionClaimToken())) {
            return currentPayoutResponse(remittance, "STALE_EXECUTION");
        }

        RemittancePayoutAttemptEntity attempt = attempts.findById(payout.attemptId())
            .orElseThrow(() -> new IllegalArgumentException("payout attempt not found"));

        RemittanceEntity.Status mapped = mapRemittanceStatus(providerStatus);
        Instant now = Instant.now();
        attempt.record(providerTransferId, mapAttemptStatus(providerStatus), null, now);
        remittance.clearPayoutExecutionClaim(payout.executionClaimToken(), now);
        remittance.markPayoutCreated(providerTransferId, mapped, now);
        if (mapped == RemittanceEntity.Status.COMPLETED) {
            payoutLedger.recordCompletedPayout(remittance, payout.provider().name(), providerTransferId);
        }

        return new PayoutResponse(
            remittance.getId(), mapped, providerTransferId, providerStatus.name()
        );
    }

    private PayoutResponse releaseAfterAllProvidersFailed(
        RemittanceEntity remittance,
        PayoutProviderType provider,
        String providerTransferId
    ) {
        RemittancePayoutRecoveryService.RecoveryResponse recovery =
            recoveryService.attemptRecovery(remittance, providerTransferId);

        return new PayoutResponse(
            recovery.remittanceId(),
            recovery.status(),
            recovery.providerTransferId(),
            recovery.recoveryStatus().equals("RECOVERY_COMPLETED")
                ? "ALL_PROVIDERS_FAILED"
                : recovery.recoveryStatus()
        );
    }

    private PayoutResponse markRetryRequired(PreparedPayout payout) {
        return transactionTemplate.execute(status -> {
            RemittanceEntity remittance = remittances.findByIdForUpdate(payout.remittanceId())
                .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

            if (!remittance.ownsPayoutExecutionClaim(payout.executionClaimToken())) {
                return currentPayoutResponse(remittance, "STALE_EXECUTION");
            }

            RemittancePayoutAttemptEntity attempt = attempts.findById(payout.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("payout attempt not found"));
            Instant now = Instant.now();
            attempt.record(payout.providerTransferId(), RemittancePayoutAttemptEntity.Status.UNKNOWN, "PAYOUT_RETRY_REQUIRED", now);
            remittance.clearPayoutExecutionClaim(payout.executionClaimToken(), now);
            return retryResponse(payout);
        });
    }

    private PayoutResponse retryResponse(PreparedPayout payout) {
        return new PayoutResponse(
            payout.remittanceId(),
            payout.status() == RemittanceEntity.Status.PAYOUT_PROCESSING
                ? RemittanceEntity.Status.PAYOUT_PROCESSING
                : RemittanceEntity.Status.PAYOUT_PENDING,
            payout.providerTransferId(),
            "PAYOUT_RETRY_REQUIRED"
        );
    }

    private PayoutResponse currentPayoutResponse(
        RemittanceEntity remittance,
        String providerStatus
    ) {
        return new PayoutResponse(
            remittance.getId(),
            remittance.getStatus(),
            remittance.getProviderTransferId(),
            providerStatus
        );
    }

    private PayoutProvider.CreatePayoutCommand commandFor(
        RemittanceEntity remittance,
        RemittanceRecipientEntity recipient,
        MobileMoneyNetworkEntity network
    ) {
        return new PayoutProvider.CreatePayoutCommand(
            remittance.getPayoutReference(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            remittance.getSourceCurrency(),
            recipient.getCountryCode(),
            recipient.getPayoutMethod(),
            network.getCode(),
            recipient.getNormalizedPhoneNumber(),
            recipient.getVerifiedName()
        );
    }

    private PreparedPayout prepared(
        RemittanceEntity remittance,
        RemittancePayoutAttemptEntity attempt,
        RemittanceRecipientEntity recipient,
        MobileMoneyNetworkEntity network,
        boolean executionClaimed
    ) {
        return new PreparedPayout(
            remittance.getId(),
            attempt.getId(),
            attempt.getProvider(),
            attempt.getProviderTransferId(),
            attempt.getPayoutReference(),
            remittance.getSourceCurrency(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            recipient.getCountryCode(),
            recipient.getPayoutMethod(),
            network.getCode(),
            recipient.getNormalizedPhoneNumber(),
            recipient.getVerifiedName(),
            remittance.getStatus(),
            executionClaimed,
            executionClaimed ? remittance.getPayoutExecutionClaimToken() : null
        );
    }

    private PreparedPayout terminalSnapshot(RemittanceEntity remittance) {
        PayoutProviderType provider = remittance.getPayoutProvider() == null
            ? defaultProvider
            : PayoutProviderType.valueOf(remittance.getPayoutProvider());

        return new PreparedPayout(
            remittance.getId(), null, provider, remittance.getProviderTransferId(),
            remittance.getPayoutReference(), remittance.getSourceCurrency(),
            remittance.getDestinationAmount(), remittance.getDestinationCurrency(),
            null, null, null, null, null, remittance.getStatus(), false, null
        );
    }

    private static Set<PayoutProviderType> attemptedProviders(
        java.util.List<RemittancePayoutAttemptEntity> history
    ) {
        Set<PayoutProviderType> result = new HashSet<>();
        history.forEach(attempt -> result.add(attempt.getProvider()));
        return result;
    }

    private static RemittancePayoutAttemptEntity.Status mapAttemptStatus(PayoutProvider.PayoutStatus status) {
        return switch (status) {
            case CREATED -> RemittancePayoutAttemptEntity.Status.CREATED;
            case PENDING -> RemittancePayoutAttemptEntity.Status.PENDING;
            case PROCESSING -> RemittancePayoutAttemptEntity.Status.PROCESSING;
            case COMPLETED -> RemittancePayoutAttemptEntity.Status.COMPLETED;
            case FAILED -> RemittancePayoutAttemptEntity.Status.FAILED;
            case CANCELLED -> RemittancePayoutAttemptEntity.Status.CANCELLED;
            case UNKNOWN -> RemittancePayoutAttemptEntity.Status.UNKNOWN;
        };
    }

    private static RemittanceEntity.Status mapRemittanceStatus(PayoutProvider.PayoutStatus status) {
        return switch (status) {
            case CREATED, PENDING, PROCESSING, UNKNOWN -> RemittanceEntity.Status.PAYOUT_PROCESSING;
            case COMPLETED -> RemittanceEntity.Status.COMPLETED;
            case FAILED -> RemittanceEntity.Status.FAILED;
            case CANCELLED -> RemittanceEntity.Status.CANCELLED;
        };
    }

    private static PayoutProvider.PayoutStatus mapProviderStatus(String status) {
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "NEW", "INITIATED", "CREATED" -> PayoutProvider.PayoutStatus.CREATED;
            case "PENDING", "PROCESSING", "OTP", "RECEIVED" -> PayoutProvider.PayoutStatus.PENDING;
            case "SUCCESSFUL", "SUCCESS", "COMPLETED" -> PayoutProvider.PayoutStatus.COMPLETED;
            case "FAILED", "ERROR", "REVERSED", "ABANDONED", "BLOCKED", "REJECTED" -> PayoutProvider.PayoutStatus.FAILED;
            case "CANCELLED", "CANCELED" -> PayoutProvider.PayoutStatus.CANCELLED;
            default -> PayoutProvider.PayoutStatus.UNKNOWN;
        };
    }

    private static boolean isTerminal(RemittanceEntity.Status status) {
        return status == RemittanceEntity.Status.COMPLETED
            || status == RemittanceEntity.Status.FAILED
            || status == RemittanceEntity.Status.CANCELLED
            || status == RemittanceEntity.Status.RECOVERY_REQUIRED;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }


    public record PayoutResponse(
        UUID remittanceId,
        RemittanceEntity.Status status,
        String providerTransferId,
        String providerStatus
    ) {}
}
