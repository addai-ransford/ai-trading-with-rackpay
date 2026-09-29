package com.rackpay.api.remittance.core.service;

import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.providers.mollie.Amount;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;
import com.rackpay.api.remittance.adapters.out.persistence.payout;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.shared.core.service.must;
import com.rackpay.api.wallet.core.model.Wallet;
import com.rackpay.api.wallet.core.model.WalletId;

import com.rackpay.api.shared.core.money.Money;
import com.rackpay.api.shared.core.transaction.IdempotencyKey;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceJpaRepository;
import com.rackpay.api.wallet.core.service.WalletCreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RemittancePayoutRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(RemittancePayoutRecoveryService.class);
    private static final Duration RECOVERY_CLAIM_TTL = Duration.ofSeconds(30);
    private static final Duration RECOVERY_WAIT = Duration.ofMillis(50);
    private static final int RECOVERY_WAIT_ATTEMPTS = 200;

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
     * Recovers reserved funds after all payout providers have definitively failed.
     *
     * The remittance row is locked only long enough to claim recovery. The claim is
     * committed before the wallet mutation starts, so a REQUIRES_NEW transaction
     * never competes with another recovery worker holding the same remittance lock.
     * The wallet mutation itself is idempotent by the deterministic release key.
     */
    public RecoveryResponse recover(UUID remittanceId, String providerTransferId) {
        RecoveryClaim claim = waitForOrClaimRecovery(remittanceId, providerTransferId);
        if (claim.response() != null) return claim.response();

        LedgerRecovery recovery = claim.recovery();

        try {
            recoveryTransactionTemplate.executeWithoutResult(status -> {
                UUID clearingAccountId = clearingAccounts.require(recovery.currency()).getId();
                walletCredit.credit(
                    new IdempotencyKey("remittance:release:" + remittanceId),
                    sha256(
                        remittanceId + "|release|" + recovery.amount().toPlainString()
                            + "|" + recovery.currency().name()
                    ),
                    claim.walletId(),
                    clearingAccountId,
                    new Money(recovery.amount(), recovery.currency())
                );
            });

            return transactionTemplate.execute(status -> {
                RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
                    .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

                if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
                    return new RecoveryResponse(
                        remittanceId, RemittanceEntity.Status.FAILED,
                        providerTransferId, "ALREADY_RECOVERED"
                    );
                }

                remittance.markPayoutCreated(
                    providerTransferId,
                    RemittanceEntity.Status.FAILED,
                    Instant.now()
                );
                remittance.clearPayoutExecutionClaim(Instant.now());

                return new RecoveryResponse(
                    remittanceId, RemittanceEntity.Status.FAILED,
                    providerTransferId, "RECOVERY_COMPLETED"
                );
            });
        } catch (RuntimeException ex) {
            log.warn("Remittance payout recovery failed for {}", remittanceId, ex);
            return transactionTemplate.execute(status -> {
                RemittanceEntity remittance = remittances.findByIdForUpdate(remittanceId)
                    .orElseThrow(() -> new IllegalArgumentException("remittance not found"));

                if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
                    return new RecoveryResponse(
                        remittanceId, RemittanceEntity.Status.FAILED,
                        providerTransferId, "ALREADY_RECOVERED"
                    );
                }

                if (remittance.getStatus() != RemittanceEntity.Status.RECOVERY_REQUIRED) {
                    remittance.markRecoveryRequired(providerTransferId, Instant.now());
                }
                remittance.clearPayoutExecutionClaim(Instant.now());

                return new RecoveryResponse(
                    remittanceId, RemittanceEntity.Status.RECOVERY_REQUIRED,
                    providerTransferId, "RECOVERY_REQUIRED"
                );
            });
        }
    }

    /**
     * Called by the payout state machine while its transaction already holds the
     * remittance lock. This method only persists the recovery-required state.
     * The actual wallet mutation must happen after the caller commits.
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

        if (remittance.getStatus() != RemittanceEntity.Status.RECOVERY_REQUIRED) {
            remittance.markRecoveryRequired(providerTransferId, Instant.now());
        }
        remittance.clearPayoutExecutionClaim(Instant.now());

        return new RecoveryResponse(
            remittance.getId(), RemittanceEntity.Status.RECOVERY_REQUIRED,
            providerTransferId, "RECOVERY_REQUIRED"
        );
    }

    private RecoveryClaim waitForOrClaimRecovery(UUID remittanceId, String providerTransferId) {
        for (int attempt = 0; attempt < RECOVERY_WAIT_ATTEMPTS; attempt++) {
            RecoveryClaim claim = transactionTemplate.execute(status -> {
                RemittanceEntity remittance = remittances.findByIdForUpdateSkipLocked(remittanceId)
                    .orElse(null);

                if (remittance == null) return RecoveryClaim.waiting();

                if (remittance.getStatus() == RemittanceEntity.Status.FAILED) {
                    return RecoveryClaim.response(new RecoveryResponse(
                        remittanceId, RemittanceEntity.Status.FAILED,
                        providerTransferId, "ALREADY_RECOVERED"
                    ));
                }

                if (remittance.getStatus() != RemittanceEntity.Status.FUNDS_RESERVED
                    && remittance.getStatus() != RemittanceEntity.Status.PAYOUT_PENDING
                    && remittance.getStatus() != RemittanceEntity.Status.PAYOUT_PROCESSING
                    && remittance.getStatus() != RemittanceEntity.Status.RECOVERY_REQUIRED) {
                    return RecoveryClaim.response(new RecoveryResponse(
                        remittanceId, remittance.getStatus(),
                        providerTransferId, remittance.getStatus().name()
                    ));
                }

                Instant now = Instant.now();
                if (remittance.hasActivePayoutExecutionClaim(now)) {
                    return RecoveryClaim.waiting();
                }

                LedgerRecovery recovery = new LedgerRecovery(
                    remittance.getSourceAmount().add(remittance.getFeeAmount()),
                    remittance.getSourceCurrency()
                );

                remittance.markRecoveryRequired(providerTransferId, now);
                remittance.claimPayoutExecution(
                    UUID.randomUUID(),
                    now.plus(RECOVERY_CLAIM_TTL),
                    now
                );

                return RecoveryClaim.claimed(
                    recovery,
                    remittance.getWalletId()
                );
            });

            if (!claim.inProgress()) return claim;

            try {
                Thread.sleep(RECOVERY_WAIT.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for remittance recovery", ex);
            }
        }

        throw new IllegalStateException("timed out waiting for remittance recovery claim");
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

    private record LedgerRecovery(BigDecimal amount, com.rackpay.api.shared.core.money.Currency currency) {}

    private record RecoveryClaim(
        LedgerRecovery recovery,
        UUID walletId,
        RecoveryResponse response,
        boolean inProgress
    ) {
        static RecoveryClaim claimed(LedgerRecovery recovery, UUID walletId) {
            return new RecoveryClaim(recovery, walletId, null, false);
        }

        static RecoveryClaim response(RecoveryResponse response) {
            return new RecoveryClaim(null, null, response, false);
        }

        static RecoveryClaim waiting() {
            return new RecoveryClaim(null, null, null, true);
        }
    }

    public record RecoveryResponse(
        UUID remittanceId,
        RemittanceEntity.Status status,
        String providerTransferId,
        String recoveryStatus
    ) {}
}
