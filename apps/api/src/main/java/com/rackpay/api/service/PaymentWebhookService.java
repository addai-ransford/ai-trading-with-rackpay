package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.payment.PaymentProvider;
import com.rackpay.api.payment.PaymentProviderRegistry;
import com.rackpay.api.payment.PaymentProviderType;
import com.rackpay.api.persistence.payment.PaymentAdjustmentEntity;
import com.rackpay.api.persistence.payment.PaymentProviderEventEntity;
import com.rackpay.api.persistence.payment.PaymentProviderEventJpaRepository;
import com.rackpay.api.persistence.payment.PaymentTransactionEntity;
import com.rackpay.api.persistence.payment.PaymentTransactionJpaRepository;
import com.rackpay.api.service.WalletBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@Service
public class PaymentWebhookService {
    private final PaymentProviderRegistry registry;
    private final PaymentProviderEventJpaRepository events;
    private final PaymentTransactionJpaRepository payments;
    private final WalletBalanceService balances;
    private final WalletCreditService walletCredit;
    private final PaymentSettlementAccountService settlementAccounts;
    private final PaymentAdjustmentService adjustments;

    public PaymentWebhookService(
        PaymentProviderRegistry registry,
        PaymentProviderEventJpaRepository events,
        PaymentTransactionJpaRepository payments,
        WalletBalanceService balances,
        WalletCreditService walletCredit,
        PaymentSettlementAccountService settlementAccounts,
        PaymentAdjustmentService adjustments
    ) {
        this.registry = registry;
        this.events = events;
        this.payments = payments;
        this.balances = balances;
        this.walletCredit = walletCredit;
        this.settlementAccounts = settlementAccounts;
        this.adjustments = adjustments;
    }

    @Transactional
    public void handle(PaymentProviderType providerType, String payload, Map<String, String> headers) {
        PaymentProvider provider = registry.require(providerType);
        PaymentProvider.WebhookResult result = provider.handleWebhook(payload, headers);

        if (result.eventId() == null || result.eventId().isBlank()) {
            throw new IllegalArgumentException("Provider webhook has no event id");
        }
        if (result.providerPaymentId() == null || result.providerPaymentId().isBlank()) {
            throw new IllegalArgumentException("Provider webhook has no payment id");
        }

        String lockKey = providerType.name() + ":" + result.eventId();
        events.lockEvent(lockKey);

        if (events.findByProviderAndEventId(providerType, result.eventId())
            .map(e -> "PROCESSED".equals(e.getStatus()))
            .orElse(false)) {
            return;
        }

        PaymentTransactionEntity payment =
            payments.findByProviderAndProviderPaymentIdForUpdate(
                providerType,
                result.providerPaymentId()
            ).orElseThrow(() ->
                new IllegalArgumentException(
                    "Unknown payment transaction: " + result.providerPaymentId()
                )
            );

        PaymentProviderEventEntity event = events.findByProviderAndEventId(
                providerType, result.eventId()
            ).orElseGet(() -> events.save(new PaymentProviderEventEntity(
                java.util.UUID.randomUUID(),
                providerType,
                result.eventId(),
                result.providerPaymentId(),
                result.rawEventType(),
                Instant.now()
            )));

        switch (result.status()) {
            case PAID -> handlePaid(payment, result);
            case REFUNDED -> {
                BigDecimalAmount verified = verifiedAdjustmentAmount(payment, result);
                adjustments.reverseWalletCredit(
                    payment,
                    PaymentAdjustmentEntity.Type.REFUND,
                    result.eventId(),
                    verified.value()
                );
                payment.markStatus("REFUNDED", Instant.now());
            }
            case CHARGED_BACK -> {
                BigDecimalAmount verified = verifiedAdjustmentAmount(payment, result);
                adjustments.reverseWalletCredit(
                    payment,
                    PaymentAdjustmentEntity.Type.CHARGEBACK,
                    result.eventId(),
                    verified.value()
                );
                payment.markStatus("CHARGEBACK", Instant.now());
            }
            case FAILED -> payment.markStatus("FAILED", Instant.now());
            case CANCELLED -> payment.markStatus("CANCELLED", Instant.now());
            default -> payment.markStatus(result.status().name(), Instant.now());
        }

        event.markProcessed(Instant.now());
    }

    private void handlePaid(
        PaymentTransactionEntity payment,
        PaymentProvider.WebhookResult result
    ) {
        if (result.amount() == null || result.currency() == null) {
            throw new IllegalStateException(
                "Provider webhook did not contain a verified amount and currency"
            );
        }

        if (payment.getAmount().compareTo(result.amount()) != 0 ||
            payment.getCurrency() != result.currency()) {
            throw new SecurityException(
                "Provider payment amount or currency does not match RackPay transaction"
            );
        }

        balances.openBalance(payment.getWalletId(), payment.getCurrency());
        java.util.UUID clearingAccount =
            settlementAccounts.requireAccount(providerType(payment), payment.getCurrency());

        String key = "payment-credit:" +
            payment.getProvider().name() + ":" + payment.getProviderPaymentId();

        String hash = sha256(
            payment.getProvider().name() + "|" +
            payment.getProviderPaymentId() + "|" +
            payment.getAmount().toPlainString() + "|" +
            payment.getCurrency().name() + "|PAID"
        );

        var financial = walletCredit.credit(
            new IdempotencyKey(key),
            hash,
            payment.getWalletId(),
            clearingAccount,
            new Money(payment.getAmount(), payment.getCurrency())
        );

        payment.markStatus("PAID", Instant.now());
        payment.linkFinancialTransaction(financial.getId(), Instant.now());
    }

    private BigDecimalAmount verifiedAdjustmentAmount(
        PaymentTransactionEntity payment,
        PaymentProvider.WebhookResult result
    ) {
        if (result.amount() == null || result.currency() == null) {
            throw new IllegalStateException(
                "Provider adjustment webhook did not contain a verified amount and currency"
            );
        }

        if (payment.getCurrency() != result.currency()) {
            throw new SecurityException(
                "Provider adjustment currency does not match RackPay transaction"
            );
        }

        if (result.amount().compareTo(payment.getAmount()) > 0) {
            throw new SecurityException(
                "Provider adjustment exceeds RackPay transaction amount"
            );
        }

        return new BigDecimalAmount(result.amount());
    }

    private PaymentProviderType providerType(PaymentTransactionEntity payment) {
        return payment.getProvider();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash provider event", e);
        }
    }

    private record BigDecimalAmount(java.math.BigDecimal value) {}
}
