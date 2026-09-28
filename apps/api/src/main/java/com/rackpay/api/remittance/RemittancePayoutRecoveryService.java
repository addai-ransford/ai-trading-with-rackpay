package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittanceJpaRepository;
import com.rackpay.api.service.WalletCreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class RemittancePayoutRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(RemittancePayoutRecoveryService.class);
    private final RemittanceJpaRepository remittances;
    private final RemittanceClearingAccountService clearingAccounts;
    private final WalletCreditService walletCredit;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate recoveryTransactionTemplate;

    public RemittancePayoutRecoveryService(
        RemittanceJpaRepository remittances,
        RemittanceClearingAccountService clearingAccounts,
        WalletCreditService walletCredit,
        PlatformTransactionManager transactionManager
    ) {
        this.remittances = remittances;
        this.clearingAccounts = clearingAccounts;
        this.walletCredit = walletCredit;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.recoveryTransactionTemplate = new TransactionTemplate(transactionManager);
        this.recoveryTransactionTemplate.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /**
     * Retries a previously persisted RECOVERY_REQUIRED remittance.
     *
     * The wallet credit is idempotent and runs in an independent transaction so
     * a failed recovery cannot mark the enclosing remittance transaction rollback-only.
     */
    public RecoveryResponse recover(UUID remittanceId, String providerTransferId) {
        return transactionTemplate.execute(status -> {
            RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
                .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

            return attemptRecovery(remittance, providerTransferId);
        });
    }

    /**
     * Used by the payout state machine while it already holds the remittance lock.
     * The wallet mutation is isolated in its own physical transaction.
     */
    public RecoveryResponse attemptRecovery(
        RemittanceEntity remittance,
        String providerTransferId
    ) {
        if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
            return new RecoveryResponse(
                remittance.getId(), remittance.getStatus(), providerTransferId, "ALREADY_RECOVERED"
            );
        }

        if (remittance.getStatus() != RemittanceEntity.Status.FUNDS_RESERVED
            && remittance.getStatus() != RemittanceEntity.Status.PAYOUT_PENDING
            && remittance.getStatus() != RemittanceEntity.Status.PAYOUT_PROCESSING
            && remittance.getStatus() != RemittanceEntity.Status.RECOVERY_REQUIRED) {
            return new RecoveryResponse(
                remittance.getId(), remittance.getStatus(), providerTransferId,
                remittance.getStatus().name()
            );
        }

        LedgerRecovery recovery = new LedgerRecovery(
            remittance.getSourceAmount().add(remittance.getFeeAmount()),
            remittance.getSourceCurrency()
        );

        try {
            recoveryTransactionTemplate.executeWithoutResult(status -> {
                UUID clearingAccountId = clearingAccounts.require(recovery.currency()).getId();
                walletCredit.credit(
                    new IdempotencyKey("remittance:release:" + remittance.getId()),
                    sha256(
                        remittance.getId() + "|release|" + recovery.amount().toPlainString()
                            + "|" + recovery.currency().name()
                    ),
                    remittance.getWalletId(),
                    clearingAccountId,
                    new Money(recovery.amount(), recovery.currency())
                );
            });

            remittance.markPayoutCreated(
                providerTransferId,
                RemittanceEntity.Status.FAILED,
                Instant.now()
            );

            return new RecoveryResponse(
                remittance.getId(), RemittanceEntity.Status.FAILED, providerTransferId,
                "RECOVERY_COMPLETED"
            );
        } catch (RuntimeException ex) {
            log.warn("Remittance payout recovery failed for {}", remittance.getId(), ex);
            if (remittance.getStatus() != RemittanceEntity.Status.RECOVERY_REQUIRED) {
                remittance.markPayoutCreated(
                    providerTransferId,
                    RemittanceEntity.Status.RECOVERY_REQUIRED,
                    Instant.now()
                );
            }
            return new RecoveryResponse(
                remittance.getId(), RemittanceEntity.Status.RECOVERY_REQUIRED,
                providerTransferId, "RECOVERY_REQUIRED"
            );
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record LedgerRecovery(BigDecimal amount, com.rackpay.api.domain.money.Currency currency) {}

    public record RecoveryResponse(
        UUID remittanceId,
        RemittanceEntity.Status status,
        String providerTransferId,
        String recoveryStatus
    ) {}
}
