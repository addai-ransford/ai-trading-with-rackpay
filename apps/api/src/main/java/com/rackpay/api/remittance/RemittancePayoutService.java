package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.persistence.remittance.MobileMoneyNetworkEntity;
import com.rackpay.api.persistence.remittance.MobileMoneyNetworkJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittanceJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceRecipientEntity;
import com.rackpay.api.persistence.remittance.RemittanceRecipientJpaRepository;
import com.rackpay.api.service.WalletCreditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class RemittancePayoutService {
    private final RemittanceJpaRepository remittances;
    private final RemittanceRecipientJpaRepository recipients;
    private final MobileMoneyNetworkJpaRepository networks;
    private final PayoutProviderRegistry providers;
    private final WalletCreditService walletCredit;
    private final RemittanceClearingAccountService clearingAccounts;
    private final PayoutProviderType defaultProvider;
    private final TransactionTemplate transactionTemplate;

    public RemittancePayoutService(
        RemittanceJpaRepository remittances,
        RemittanceRecipientJpaRepository recipients,
        MobileMoneyNetworkJpaRepository networks,
        PayoutProviderRegistry providers,
        WalletCreditService walletCredit,
        RemittanceClearingAccountService clearingAccounts,
        @Value("${rackpay.payout.default-provider:FLUTTERWAVE}") PayoutProviderType defaultProvider,
        PlatformTransactionManager transactionManager
    ) {
        this.remittances = remittances;
        this.recipients = recipients;
        this.networks = networks;
        this.providers = providers;
        this.walletCredit = walletCredit;
        this.clearingAccounts = clearingAccounts;
        this.defaultProvider = defaultProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PayoutResponse execute(UUID remittanceId) {
        PreparedPayout payout = transactionTemplate.execute(
            status -> prepareInternal(remittanceId)
        );
        if (payout == null) {
            throw new IllegalStateException("Unable to prepare remittance payout");
        }

        if (payout.status() == RemittanceEntity.Status.COMPLETED
            || payout.status() == RemittanceEntity.Status.FAILED
            || payout.status() == RemittanceEntity.Status.CANCELLED
            || payout.status() == RemittanceEntity.Status.RECOVERY_REQUIRED) {
            return new PayoutResponse(
                payout.remittanceId(),
                payout.status(),
                payout.providerTransferId(),
                payout.status().name()
            );
        }

        PayoutProvider provider = providers.require(defaultProvider);

        try {
            if (payout.providerTransferId() != null) {
                PayoutProvider.PayoutStatus providerStatus =
                    provider.getPayout(payout.providerTransferId());

                return transactionTemplate.execute(status ->
                    applyProviderStatusInternal(
                        payout.remittanceId(),
                        providerStatus,
                        payout.providerTransferId()
                    )
                );
            }

            PayoutProvider.PayoutResult result =
                provider.findPayoutByReference(payout.payoutReference());

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

            if (result == null || result.providerTransferId() == null
                || result.providerTransferId().isBlank() || result.status() == null) {
                throw new IllegalStateException("Payout provider returned an invalid transfer response");
            }

            final PayoutProvider.PayoutResult finalResult = result;
            final PayoutProvider.PayoutStatus providerStatus =
                result.status().startsWith("FAILED:")
                    ? PayoutProvider.PayoutStatus.FAILED
                    : mapProviderStatus(result.status());

            if (providerStatus == PayoutProvider.PayoutStatus.FAILED
                || providerStatus == PayoutProvider.PayoutStatus.CANCELLED) {
                return transactionTemplate.execute(status ->
                    failAndReleaseInternal(
                        payout.remittanceId(),
                        finalResult.providerTransferId(),
                        providerStatus
                    )
                );
            }

            return transactionTemplate.execute(status ->
                recordProviderStatusInternal(
                    payout.remittanceId(),
                    finalResult.providerTransferId(),
                    providerStatus
                )
            );
        } catch (RuntimeException ex) {
            return new PayoutResponse(
                payout.remittanceId(),
                RemittanceEntity.Status.PAYOUT_PENDING,
                payout.providerTransferId(),
                "PAYOUT_RETRY_REQUIRED"
            );
        }
    }

    private PreparedPayout prepareInternal(UUID remittanceId) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (remittance.getStatus() == RemittanceEntity.Status.COMPLETED
            || remittance.getStatus() == RemittanceEntity.Status.FAILED
            || remittance.getStatus() == RemittanceEntity.Status.CANCELLED
            || remittance.getStatus() == RemittanceEntity.Status.RECOVERY_REQUIRED) {
            return terminalSnapshot(remittance);
        }

        RemittanceRecipientEntity recipient = recipients.findById(remittance.getRecipientId())
            .orElseThrow(() -> new IllegalStateException("remittance recipient not found"));

        MobileMoneyNetworkEntity network = networks.findById(recipient.getMobileMoneyNetworkId())
            .orElseThrow(() -> new IllegalStateException("mobile money network not found"));

        String payoutReference = remittance.getPayoutReference();
        if (payoutReference == null || payoutReference.isBlank()) {
            payoutReference = "RP-" + remittance.getId().toString().replace("-", "");
            remittance.setPayoutPending(defaultProvider.name(), payoutReference, Instant.now());
        }

        return new PreparedPayout(
            remittance.getId(),
            remittance.getProviderTransferId(),
            payoutReference,
            remittance.getSourceCurrency(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            recipient.getCountryCode(),
            recipient.getPayoutMethod(),
            network.getCode(),
            recipient.getNormalizedPhoneNumber(),
            recipient.getVerifiedName(),
            remittance.getStatus()
        );
    }

    private PreparedPayout terminalSnapshot(RemittanceEntity remittance) {
        return new PreparedPayout(
            remittance.getId(),
            remittance.getProviderTransferId(),
            remittance.getPayoutReference(),
            remittance.getSourceCurrency(),
            remittance.getDestinationAmount(),
            remittance.getDestinationCurrency(),
            null,
            null,
            null,
            null,
            null,
            remittance.getStatus()
        );
    }

    private PayoutResponse recordProviderStatusInternal(
        UUID remittanceId,
        String providerTransferId,
        PayoutProvider.PayoutStatus status
    ) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        RemittanceEntity.Status mapped = mapRemittanceStatus(status);
        remittance.markPayoutCreated(providerTransferId, mapped, Instant.now());

        return new PayoutResponse(remittanceId, mapped, providerTransferId, status.name());
    }

    private PayoutResponse applyProviderStatusInternal(
        UUID remittanceId,
        PayoutProvider.PayoutStatus status,
        String providerTransferId
    ) {
        if (status == PayoutProvider.PayoutStatus.FAILED
            || status == PayoutProvider.PayoutStatus.CANCELLED) {
            return failAndReleaseInternal(remittanceId, providerTransferId, status);
        }

        return recordProviderStatusInternal(remittanceId, providerTransferId, status);
    }

    private PayoutResponse failAndReleaseInternal(
        UUID remittanceId,
        String providerTransferId,
        PayoutProvider.PayoutStatus providerStatus
    ) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
            return new PayoutResponse(
                remittanceId,
                remittance.getStatus(),
                providerTransferId,
                "ALREADY_RELEASED"
            );
        }

        var clearingAccount = clearingAccounts.require(remittance.getSourceCurrency());

        try {
            BigDecimal total = remittance.getSourceAmount().add(remittance.getFeeAmount());

            walletCredit.credit(
                new IdempotencyKey("remittance:release:" + remittanceId),
                sha256(
                    remittanceId + "|release|" + total.toPlainString()
                        + "|" + remittance.getSourceCurrency().name()
                ),
                remittance.getWalletId(),
                clearingAccount.getId(),
                new Money(total, remittance.getSourceCurrency())
            );

            remittance.markPayoutCreated(
                providerTransferId,
                RemittanceEntity.Status.FAILED,
                Instant.now()
            );

            return new PayoutResponse(
                remittanceId,
                RemittanceEntity.Status.FAILED,
                providerTransferId,
                providerStatus.name()
            );
        } catch (RuntimeException ex) {
            remittance.markPayoutCreated(
                providerTransferId,
                RemittanceEntity.Status.RECOVERY_REQUIRED,
                Instant.now()
            );

            return new PayoutResponse(
                remittanceId,
                RemittanceEntity.Status.RECOVERY_REQUIRED,
                providerTransferId,
                "RECOVERY_REQUIRED"
            );
        }
    }

    private static RemittanceEntity.Status mapRemittanceStatus(PayoutProvider.PayoutStatus status) {
        return switch (status) {
            case CREATED, PENDING, PROCESSING -> RemittanceEntity.Status.PAYOUT_PROCESSING;
            case COMPLETED -> RemittanceEntity.Status.COMPLETED;
            case FAILED -> RemittanceEntity.Status.FAILED;
            case CANCELLED -> RemittanceEntity.Status.CANCELLED;
            case UNKNOWN -> RemittanceEntity.Status.PAYOUT_PENDING;
        };
    }

    private static PayoutProvider.PayoutStatus mapProviderStatus(String status) {
        try {
            return PayoutProvider.PayoutStatus.valueOf(
                status.toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException ignored) {
            return switch (status.toUpperCase(java.util.Locale.ROOT)) {
                case "NEW", "INITIATED" -> PayoutProvider.PayoutStatus.CREATED;
                case "SUCCESSFUL", "SUCCESS", "COMPLETED" -> PayoutProvider.PayoutStatus.COMPLETED;
                case "FAILED", "ERROR" -> PayoutProvider.PayoutStatus.FAILED;
                case "CANCELLED", "CANCELED" -> PayoutProvider.PayoutStatus.CANCELLED;
                default -> PayoutProvider.PayoutStatus.UNKNOWN;
            };
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record PreparedPayout(
        UUID remittanceId,
        String providerTransferId,
        String payoutReference,
        com.rackpay.api.domain.money.Currency sourceCurrency,
        BigDecimal destinationAmount,
        com.rackpay.api.domain.money.Currency destinationCurrency,
        String countryCode,
        PayoutMethod payoutMethod,
        String networkCode,
        String normalizedPhoneNumber,
        String recipientName,
        RemittanceEntity.Status status
    ) {}

    public record PayoutResponse(
        UUID remittanceId,
        RemittanceEntity.Status status,
        String providerTransferId,
        String providerStatus
    ) {}
}
