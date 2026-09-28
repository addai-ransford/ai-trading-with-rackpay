package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.remittance.MobileMoneyNetworkEntity;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittancePayoutAttemptEntity;
import com.rackpay.api.persistence.remittance.RemittancePayoutAttemptJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceRecipientEntity;
import com.rackpay.api.persistence.remittance.RemittanceRecipientJpaRepository;
import com.rackpay.api.service.WalletCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemittancePayoutServiceTest {

    private final RemittanceJpaRepository remittances = mock(RemittanceJpaRepository.class);
    private final RemittanceRecipientJpaRepository recipients = mock(RemittanceRecipientJpaRepository.class);
    private final com.rackpay.api.persistence.remittance.MobileMoneyNetworkJpaRepository networks =
        mock(com.rackpay.api.persistence.remittance.MobileMoneyNetworkJpaRepository.class);
    private final RemittancePayoutAttemptJpaRepository attempts =
        mock(RemittancePayoutAttemptJpaRepository.class);
    private final PayoutProviderRegistry providers = mock(PayoutProviderRegistry.class);
    private final WalletCreditService walletCredit = mock(WalletCreditService.class);
    private final RemittanceClearingAccountService clearingAccounts =
        mock(RemittanceClearingAccountService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);

    private RemittancePayoutService service;
    private UUID remittanceId;
    private UUID recipientId;
    private UUID networkId;
    private RemittanceEntity remittance;
    private RemittanceRecipientEntity recipient;
    private MobileMoneyNetworkEntity network;
    private PayoutProvider flutterwave;
    private PayoutProvider paystack;
    private List<RemittancePayoutAttemptEntity> history;

    @BeforeEach
    void setUp() {
        service = new RemittancePayoutService(
            remittances,
            recipients,
            networks,
            attempts,
            providers,
            walletCredit,
            clearingAccounts,
            PayoutProviderType.FLUTTERWAVE,
            transactionManager
        );

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doNothing().when(transactionManager).commit(any());
        doNothing().when(transactionManager).rollback(any());

        remittanceId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        networkId = UUID.randomUUID();

        remittance = mock(RemittanceEntity.class);
        recipient = mock(RemittanceRecipientEntity.class);
        network = mock(MobileMoneyNetworkEntity.class);
        flutterwave = mock(PayoutProvider.class);
        paystack = mock(PayoutProvider.class);

        when(remittance.getId()).thenReturn(remittanceId);
        when(remittance.getStatus()).thenReturn(RemittanceEntity.Status.FUNDS_RESERVED);
        when(remittance.getRecipientId()).thenReturn(recipientId);
        when(remittance.getSourceCurrency()).thenReturn(Currency.EUR);
        when(remittance.getDestinationCurrency()).thenReturn(Currency.GHS);
        when(remittance.getSourceAmount()).thenReturn(new BigDecimal("100.00"));
        when(remittance.getDestinationAmount()).thenReturn(new BigDecimal("1250.00"));
        when(remittance.getFeeAmount()).thenReturn(new BigDecimal("2.50"));
        when(remittance.getPayoutReference()).thenReturn("rp-existing");

        when(recipient.getMobileMoneyNetworkId()).thenReturn(networkId);
        when(recipient.getCountryCode()).thenReturn("GH");
        when(recipient.getPayoutMethod()).thenReturn(PayoutMethod.MOBILE_MONEY);
        when(recipient.getNormalizedPhoneNumber()).thenReturn("+233241234567");
        when(recipient.getVerifiedName()).thenReturn("Test Recipient");

        when(network.getCode()).thenReturn("MTN");

        when(remittances.findByIdForUpdate(remittanceId)).thenReturn(Optional.of(remittance));
        when(recipients.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(networks.findById(networkId)).thenReturn(Optional.of(network));

        when(flutterwave.type()).thenReturn(PayoutProviderType.FLUTTERWAVE);
        when(paystack.type()).thenReturn(PayoutProviderType.PAYSTACK);
        when(providers.require(PayoutProviderType.FLUTTERWAVE)).thenReturn(flutterwave);
        when(providers.require(PayoutProviderType.PAYSTACK)).thenReturn(paystack);

        history = new ArrayList<>();
        when(attempts.findAllByRemittanceIdForUpdate(remittanceId)).thenAnswer(invocation -> history);
        when(attempts.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return history.stream()
                .filter(attempt -> attempt.getId().equals(id))
                .findFirst();
        });
        when(attempts.save(any(RemittancePayoutAttemptEntity.class))).thenAnswer(invocation -> {
            RemittancePayoutAttemptEntity attempt = invocation.getArgument(0);
            history.add(0, attempt);
            return attempt;
        });
    }

    @Test
    void failedFlutterwavePayoutFailsOverToPaystackAndCompletes() {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        when(providers.nextEligible(
            eq(PayoutProviderType.FLUTTERWAVE), any(), any()
        )).thenReturn(paystack);

        when(flutterwave.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("fw-transfer-1", "FAILED"));
        when(paystack.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("ps-transfer-1", "SUCCESSFUL"));

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.COMPLETED, response.status());
        assertEquals("ps-transfer-1", response.providerTransferId());
        assertEquals(2, history.size());
        assertEquals(PayoutProviderType.PAYSTACK, history.get(0).getProvider());
        assertEquals(RemittancePayoutAttemptEntity.Status.COMPLETED, history.get(0).getStatus());

        verify(flutterwave).createPayout(any());
        verify(paystack).createPayout(any());
        verify(walletCredit, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentPayoutExecutionAllowsOnlyOneProviderCall() throws Exception {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        AtomicBoolean claimActive = new AtomicBoolean(false);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);

        when(remittance.hasActivePayoutExecutionClaim(any()))
            .thenAnswer(invocation -> claimActive.get());
        doAnswer(invocation -> {
            claimActive.set(true);
            return null;
        }).when(remittance).claimPayoutExecution(any(), any(), any());
        doAnswer(invocation -> {
            claimActive.set(false);
            return null;
        }).when(remittance).clearPayoutExecutionClaim(any());

        when(flutterwave.createPayout(any())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertEquals(true, releaseProvider.await(5, TimeUnit.SECONDS));
            return new PayoutProvider.PayoutResult("fw-transfer-concurrent", "SUCCESSFUL");
        });

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<RemittancePayoutService.PayoutResponse> first =
                executor.submit(() -> service.execute(remittanceId));

            assertEquals(true, providerStarted.await(5, TimeUnit.SECONDS));

            java.util.concurrent.Future<RemittancePayoutService.PayoutResponse> second =
                executor.submit(() -> service.execute(remittanceId));

            RemittancePayoutService.PayoutResponse secondResponse = second.get(5, TimeUnit.SECONDS);
            assertEquals("PAYOUT_RETRY_REQUIRED", secondResponse.providerStatus());

            releaseProvider.countDown();
            RemittancePayoutService.PayoutResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            assertEquals(RemittanceEntity.Status.COMPLETED, firstResponse.status());
            assertEquals("fw-transfer-concurrent", firstResponse.providerTransferId());

            verify(flutterwave, times(1)).createPayout(any());
            verify(paystack, never()).createPayout(any());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void providerTimeoutReturnsRetryRequiredWithoutFailover() {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        when(flutterwave.createPayout(any()))
            .thenThrow(new RuntimeException("provider timeout"));

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.PAYOUT_PENDING, response.status());
        assertEquals("PAYOUT_RETRY_REQUIRED", response.providerStatus());

        verify(flutterwave).createPayout(any());
        verify(paystack, never()).createPayout(any());
        verify(providers, never()).nextEligible(any(), any(), any());
    }

    @Test
    void existingProviderTransferIsReconciledBeforeAnotherPayout() {
        RemittancePayoutAttemptEntity existing = attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing");
        existing.record("fw-transfer-1", RemittancePayoutAttemptEntity.Status.UNKNOWN, "timeout", Instant.parse("2026-09-28T08:05:00Z"));
        history.add(existing);

        when(flutterwave.getPayout("fw-transfer-1"))
            .thenReturn(PayoutProvider.PayoutStatus.COMPLETED);

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.COMPLETED, response.status());
        assertEquals("fw-transfer-1", response.providerTransferId());
        assertEquals(PayoutProvider.PayoutStatus.COMPLETED.name(), response.providerStatus());

        verify(flutterwave).getPayout("fw-transfer-1");
        verify(flutterwave, never()).createPayout(any());
        verify(paystack, never()).createPayout(any());
        verify(providers, never()).nextEligible(any(), any(), any());
    }

    @Test
    void pendingFlutterwavePayoutDoesNotCallPaystack() {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        when(flutterwave.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("fw-transfer-1", "PENDING"));

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.PAYOUT_PROCESSING, response.status());
        assertEquals("fw-transfer-1", response.providerTransferId());
        assertEquals("PENDING", response.providerStatus());

        verify(flutterwave).createPayout(any());
        verify(paystack, never()).createPayout(any());
        verify(providers, never()).nextEligible(any(), any(), any());
    }

    @Test
    void bothProvidersFailCreditWalletBackExactlyOnce() {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        when(providers.nextEligible(
            eq(PayoutProviderType.FLUTTERWAVE), any(), any()
        )).thenReturn(paystack);
        when(providers.nextEligible(
            eq(PayoutProviderType.PAYSTACK), any(), any()
        )).thenReturn(null);

        when(flutterwave.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("fw-transfer-1", "FAILED"));
        when(paystack.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("ps-transfer-1", "FAILED"));

        LedgerAccountEntity clearingAccount = mock(LedgerAccountEntity.class);
        UUID clearingAccountId = UUID.randomUUID();
        when(clearingAccount.getId()).thenReturn(clearingAccountId);
        when(clearingAccounts.require(Currency.EUR)).thenReturn(clearingAccount);

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.FAILED, response.status());
        assertEquals("ALL_PROVIDERS_FAILED", response.providerStatus());
        assertEquals(2, history.size());
        assertEquals(RemittancePayoutAttemptEntity.Status.FAILED, history.get(0).getStatus());

        verify(walletCredit, times(1)).credit(
            any(), any(), any(), eq(clearingAccountId), any()
        );
    }

    @Test
    void payoutRecoveryFailureLeavesRemittanceInRecoveryRequired() {
        history.add(attempt(PayoutProviderType.FLUTTERWAVE, 1, "rp-existing"));

        when(providers.nextEligible(
            eq(PayoutProviderType.FLUTTERWAVE), any(), any()
        )).thenReturn(paystack);
        when(providers.nextEligible(
            eq(PayoutProviderType.PAYSTACK), any(), any()
        )).thenReturn(null);

        when(flutterwave.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("fw-transfer-1", "FAILED"));
        when(paystack.createPayout(any()))
            .thenReturn(new PayoutProvider.PayoutResult("ps-transfer-1", "FAILED"));

        LedgerAccountEntity clearingAccount = mock(LedgerAccountEntity.class);
        when(clearingAccount.getId()).thenReturn(UUID.randomUUID());
        when(clearingAccounts.require(Currency.EUR)).thenReturn(clearingAccount);
        doThrow(new RuntimeException("wallet recovery unavailable"))
            .when(walletCredit).credit(any(), any(), any(), any(), any());

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.RECOVERY_REQUIRED, response.status());
        assertEquals("RECOVERY_REQUIRED", response.providerStatus());
        assertEquals(2, history.size());
        assertEquals(RemittancePayoutAttemptEntity.Status.FAILED, history.get(0).getStatus());

        verify(walletCredit, times(1)).credit(any(), any(), any(), any(), any());
    }

    @Test
    void completedRemittanceDoesNotCreateAnotherProviderPayout() {
        when(remittance.getStatus()).thenReturn(RemittanceEntity.Status.COMPLETED);
        when(remittance.getPayoutProvider()).thenReturn(PayoutProviderType.FLUTTERWAVE.name());
        when(remittance.getProviderTransferId()).thenReturn("fw-transfer-1");

        RemittancePayoutService.PayoutResponse response = service.execute(remittanceId);

        assertEquals(RemittanceEntity.Status.COMPLETED, response.status());
        assertEquals("fw-transfer-1", response.providerTransferId());

        verifyNoInteractions(flutterwave, paystack);
        verify(attempts, never()).save(any());
        verify(walletCredit, never()).credit(any(), any(), any(), any(), any());
    }

    private static RemittancePayoutAttemptEntity attempt(
        PayoutProviderType provider,
        int number,
        String reference
    ) {
        return new RemittancePayoutAttemptEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            number,
            provider,
            reference,
            Instant.parse("2026-09-28T08:00:00Z")
        );
    }
}
