package com.rackpay.api.service;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.payment.PaymentProviderType;
import com.rackpay.api.persistence.payment.PaymentAdjustmentEntity;
import com.rackpay.api.persistence.payment.PaymentAdjustmentJpaRepository;
import com.rackpay.api.persistence.payment.PaymentTransactionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentAdjustmentService {
    private final PaymentAdjustmentJpaRepository adjustments;
    private final WalletDebitService walletDebit;
    private final PaymentSettlementAccountService settlementAccounts;

    public PaymentAdjustmentService(
        PaymentAdjustmentJpaRepository adjustments,
        WalletDebitService walletDebit,
        PaymentSettlementAccountService settlementAccounts
    ) {
        this.adjustments = adjustments;
        this.walletDebit = walletDebit;
        this.settlementAccounts = settlementAccounts;
    }

    @Transactional
    public void reverseWalletCredit(
        PaymentTransactionEntity payment,
        PaymentAdjustmentEntity.Type type,
        String providerEventId,
        BigDecimal amount
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("payment adjustment amount must be positive");
        }

        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new SecurityException("payment adjustment exceeds original payment amount");
        }

        PaymentAdjustmentEntity adjustment =
            adjustments.findByPaymentTransactionIdAndAdjustmentTypeForUpdate(
                payment.getId(), type
            ).orElseGet(() -> adjustments.save(new PaymentAdjustmentEntity(
                UUID.randomUUID(),
                payment.getId(),
                payment.getProvider(),
                payment.getProviderPaymentId(),
                type,
                amount,
                payment.getCurrency(),
                providerEventId,
                Instant.now()
            )));

        if (adjustment.getStatus() == PaymentAdjustmentEntity.Status.COMPLETED ||
            adjustment.getStatus() == PaymentAdjustmentEntity.Status.PENDING_RECOVERY) {
            return;
        }

        if (adjustment.getAmount().compareTo(amount) != 0) {
            throw new SecurityException("payment adjustment amount changed for an existing adjustment");
        }

        UUID clearingAccount = settlementAccounts.requireAccount(
            payment.getProvider(), payment.getCurrency()
        );

        String key = "payment-adjustment:" +
            payment.getProvider().name() + ":" +
            payment.getProviderPaymentId() + ":" +
            type.name();

        String hash = sha256(
            key + "|" + amount.toPlainString() + "|" + payment.getCurrency().name()
        );

        try {
            var financial = walletDebit.debit(
                new IdempotencyKey(key),
                hash,
                payment.getWalletId(),
                clearingAccount,
                new Money(amount, payment.getCurrency())
            );

            adjustment.complete(financial.getId(), Instant.now());
        } catch (IllegalStateException e) {
            if (!(e instanceof InsufficientWalletFundsException)) {
                throw e;
            }
            adjustment.markPendingRecovery(Instant.now());
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash payment adjustment", e);
        }
    }
}
