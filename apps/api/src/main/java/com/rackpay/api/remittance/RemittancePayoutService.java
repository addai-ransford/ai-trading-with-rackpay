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
import org.springframework.transaction.annotation.Transactional;

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

    public RemittancePayoutService(
        RemittanceJpaRepository remittances,
        RemittanceRecipientJpaRepository recipients,
        MobileMoneyNetworkJpaRepository networks,
        PayoutProviderRegistry providers,
        WalletCreditService walletCredit,
        RemittanceClearingAccountService clearingAccounts,
        @Value("${rackpay.payout.default-provider:FLUTTERWAVE}") PayoutProviderType defaultProvider
    ) {
        this.remittances = remittances;
        this.recipients = recipients;
        this.networks = networks;
        this.providers = providers;
        this.walletCredit = walletCredit;
        this.clearingAccounts = clearingAccounts;
        this.defaultProvider = defaultProvider;
    }

    public PayoutResponse execute(UUID remittanceId) {
        PreparedPayout payout = prepare(remittanceId);
        PayoutProvider provider = providers.require(defaultProvider);

        PayoutProvider.PayoutResult result = null;
        try {
            if (payout.providerTransferId() != null) {
                PayoutProvider.PayoutStatus status = provider.getPayout(payout.providerTransferId());
                return applyProviderStatus(payout.remittanceId(), status, payout.providerTransferId());
            }

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

            if (result.providerTransferId() == null
                || result.providerTransferId().isBlank()
                || result.status() == null) {
                throw new IllegalStateException("Payout provider returned an invalid transfer response");
            }

            PayoutProvider.PayoutStatus status = result.status().startsWith("FAILED:")
                ? PayoutProvider.PayoutStatus.FAILED
                : mapProviderStatus(result.status());

            if (status == PayoutProvider.PayoutStatus.FAILED
                || status == PayoutProvider.PayoutStatus.CANCELLED) {
                return failAndRelease(payout.remittanceId(), result.providerTransferId(), status);
            }

            return recordProviderStatus(
                payout.remittanceId(),
                result.providerTransferId(),
                status
            );
        } catch (RuntimeException ex) {
            // Keep the funds reserved for technical/provider errors. The deterministic
            // payout reference allows the next retry/reconciliation attempt to discover
            // a transfer that may have been accepted before the API call failed.
            return new PayoutResponse(
                payout.remittanceId(),
                RemittanceEntity.Status.PAYOUT_PENDING,
                payout.providerTransferId(),
                "PAYOUT_RETRY_REQUIRED"
            );
        }
    }

    @Transactional
    protected PreparedPayout prepare(UUID remittanceId) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (remittance.getStatus() == RemittanceEntity.Status.COMPLETED) {
            return snapshot(remittance);
        }

        if (remittance.getStatus() == RemittanceEntity.Status.FAILED
            || remittance.getStatus() == RemittanceEntity.Status.CANCELLED) {
            return snapshot(remittance);
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

    @Transactional
    protected PayoutResponse recordProviderStatus(
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

    @Transactional
    protected PayoutResponse applyProviderStatus(
        UUID remittanceId,
        PayoutProvider.PayoutStatus status,
        String providerTransferId
    ) {
        if (status == PayoutProvider.PayoutStatus.FAILED
            || status == PayoutProvider.PayoutStatus.CANCELLED) {
            return failAndRelease(remittanceId, providerTransferId, status);
        }

        return recordProviderStatus(remittanceId, providerTransferId, status);
    }

    @Transactional
    protected PayoutResponse failAndRelease(
        UUID remittanceId,
        String providerTransferId,
        PayoutProvider.PayoutStatus providerStatus
    ) {
        RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

        if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
            return new PayoutResponse(remittanceId, remittance.getStatus(), providerTransferId, "ALREADY_RELEASED");
        }

        var clearingAccount = clearingAccounts.require(remittance.getSourceCurrency());

        try {
            BigDecimal total = remittance.getSourceAmount().add(remittance.getFeeAmount());
            walletCredit.credit(
                new IdempotencyKey("remittance:release:" + remittanceId),
                sha256(remittanceId + "|release|" + total.toPlainString() + "|" + remittance.getSourceCurrency().name()),
                remittance.getWalletId(),
                clearingAccount.getId(),
                new Money(total, remittance.getSourceCurrency())
            );
            remittance.markPayoutCreated(providerTransferId, RemittanceEntity.Status.FAILED, Instant.now());
            return new PayoutResponse(remittanceId, RemittanceEntity.Status.FAILED, providerTransferId, providerStatus.name());
        } catch (RuntimeException ex) {
            remittance.markPayoutCreated(providerTransferId, RemittanceEntity.Status.RECOVERY_REQUIRED, Instant.now());
            return new PayoutResponse(remittanceId, RemittanceEntity.Status.RECOVERY_REQUIRED, providerTransferId, "RECOVERY_REQUIRED");
        }
    }

    private PayoutResponse snapshotResponse(RemittanceEntity remittance) {
        return new PayoutResponse(
            remittance.getId(),
            remittance.getStatus(),
            remittance.getProviderTransferId(),
            remittance.getStatus().name()
        );
    }

    private PayoutResponse snapshot(UUID remittanceId) {
        RemittanceEntity remittance = remittances.findById(remittanceId)
            .orElseThrow(() -> new IllegalArgumentException("remittance not found"));
        return snapshotResponse(remittance);
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
            return PayoutProvider.PayoutStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT));
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
            for (byte b : digest) hex.append(String.format("%02x", b));
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
