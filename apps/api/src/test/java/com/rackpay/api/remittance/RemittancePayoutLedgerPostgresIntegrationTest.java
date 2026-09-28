package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import com.rackpay.api.persistence.ledger.LedgerTransactionJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
import com.rackpay.api.persistence.remittance.RemittanceJpaRepository;
import com.rackpay.api.persistence.remittance.RemittancePayoutLedgerPostingJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.rackpay.api.service.WalletCreditService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RemittancePayoutLedgerService.class)
class RemittancePayoutLedgerPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerAccountJpaRepository accounts;
    @Autowired LedgerTransactionJpaRepository transactions;
    @Autowired RemittancePayoutLedgerPostingJpaRepository postings;
    @Autowired RemittanceJpaRepository remittances;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID clearingId;
    private UUID remittanceId;
    private UUID userId;
    private UUID walletId;
    private UUID corridorId;
    private UUID recipientId;
    private UUID networkId;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("""
            TRUNCATE TABLE
                remittance_payout_ledger_postings,
                remittances,
                remittance_recipients,
                mobile_money_networks,
                remittance_corridors,
                remittance_countries,
                wallets,
                users,
                ledger_entries,
                ledger_transactions,
                ledger_accounts
            CASCADE
            """);

        clearingId = UUID.randomUUID();
        remittanceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        corridorId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        networkId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO users (id, keycloak_subject, email, first_name, last_name, status)
            VALUES (?, ?, ?, 'Integration', 'Tester', 'ACTIVE')
            """, userId, "payout-ledger-test-" + userId, "payout-ledger-" + userId + "@rackpay.local");

        jdbc.update("""
            INSERT INTO wallets (id, owner_id, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """, walletId, userId);

        jdbc.update("""
            INSERT INTO remittance_countries
                (code, name, dial_code, currency_code, enabled, send_enabled, receive_enabled)
            VALUES ('BE', 'Belgium', '+32', 'EUR', TRUE, TRUE, TRUE),
                   ('GH', 'Ghana', '+233', 'GHS', TRUE, TRUE, TRUE)
            """);

        jdbc.update("""
            INSERT INTO remittance_corridors
                (id, source_country_code, destination_country_code,
                 source_currency_code, destination_currency_code,
                 enabled, min_amount, max_amount, fee_fixed_amount,
                 fee_currency_code, fee_bps)
            VALUES (?, 'BE', 'GH', 'EUR', 'GHS', TRUE, 1, 10000, 0, 'EUR', 250)
            """, corridorId);

        jdbc.update("""
            INSERT INTO mobile_money_networks
                (id, country_code, code, name, enabled)
            VALUES (?, 'GH', 'MTN', 'MTN Mobile Money', TRUE)
            """, networkId);

        jdbc.update("""
            INSERT INTO remittance_recipients
                (id, user_id, country_code, payout_method, mobile_money_network_id, phone_number,
                 normalized_phone_number, verified_name, active)
            VALUES (?, ?, 'GH', 'MOBILE_MONEY', ?, '0241234567',
                    '+233241234567', 'Integration Recipient', TRUE)
            """, recipientId, userId, networkId);

        jdbc.update("""
            INSERT INTO remittances
                (id, user_id, wallet_id, corridor_id, recipient_id,
                 source_amount, source_currency_code, destination_amount,
                 destination_currency_code, fee_amount, fee_currency_code,
                 fx_rate, status, idempotency_key)
            VALUES (?, ?, ?, ?, ?, 100, 'EUR', 1250, 'GHS', 2.50, 'EUR',
                    12.5, 'FUNDS_RESERVED', 'payout-ledger-integration')
            """, remittanceId, userId, walletId, corridorId, recipientId);

        jdbc.update("""
            INSERT INTO ledger_accounts
                (id, name, currency_code, account_type, created_at)
            VALUES (?, 'Integration Remittance Clearing EUR', 'EUR', 'LIABILITY', CURRENT_TIMESTAMP)
            """, clearingId);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresPessimisticLockAllowsOnlyOneActivePayoutClaim() throws Exception {
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                RemittanceEntity entity = remittances.findByIdForUpdate(remittanceId).orElseThrow();
                if (entity.hasActivePayoutExecutionClaim(Instant.now())) return false;
                entity.claimPayoutExecution(
                    UUID.randomUUID(),
                    Instant.now().plusSeconds(30),
                    Instant.now()
                );
                remittances.saveAndFlush(entity);
                firstClaimed.countDown();
                try {
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release first claim");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while holding payout claim", ex);
                }
                return true;
            }));

            if (!firstClaimed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first transaction did not claim payout");
            }

            Future<Boolean> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                RemittanceEntity entity = remittances.findByIdForUpdate(remittanceId).orElseThrow();
                if (entity.hasActivePayoutExecutionClaim(Instant.now())) return false;
                entity.claimPayoutExecution(
                    UUID.randomUUID(),
                    Instant.now().plusSeconds(30),
                    Instant.now()
                );
                remittances.saveAndFlush(entity);
                return true;
            }));

            Thread.sleep(250);
            releaseFirst.countDown();

            assertEquals(true, first.get(5, TimeUnit.SECONDS));
            assertEquals(false, second.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedPayoutRecoveryIsIdempotentInPostgres() {
        WalletCreditService walletCredit = Mockito.mock(WalletCreditService.class);
        RemittancePayoutRecoveryService recoveryService =
            new RemittancePayoutRecoveryService(
                remittances,
                new RemittanceClearingAccountService(accounts),
                walletCredit,
                transactionManager
            );

        RemittancePayoutRecoveryService.RecoveryResponse first =
            recoveryService.recover(remittanceId, "provider-transfer-recovery-1");

        RemittancePayoutRecoveryService.RecoveryResponse second =
            recoveryService.recover(remittanceId, "provider-transfer-recovery-1");

        assertEquals(RemittanceEntity.Status.FAILED, first.status());
        assertEquals("RECOVERY_COMPLETED", first.recoveryStatus());
        assertEquals(RemittanceEntity.Status.FAILED, second.status());
        assertEquals("ALREADY_RECOVERED", second.recoveryStatus());

        Mockito.verify(walletCredit, Mockito.times(1)).credit(
            Mockito.any(),
            Mockito.anyString(),
            Mockito.eq(walletId),
            Mockito.any(),
            Mockito.any()
        );

        assertEquals(
            "FAILED",
            jdbc.queryForObject(
                "SELECT status FROM remittances WHERE id = ?",
                String.class,
                remittanceId
            )
        );
    }

    @Test
    void recoveryFailurePersistsRecoveryRequiredAndRetryIsSafe() {
        WalletCreditService walletCredit = Mockito.mock(WalletCreditService.class);
        Mockito.doThrow(new IllegalStateException("wallet temporarily unavailable"))
            .when(walletCredit)
            .credit(
                Mockito.any(),
                Mockito.anyString(),
                Mockito.eq(walletId),
                Mockito.any(),
                Mockito.any()
            );

        RemittancePayoutRecoveryService recoveryService =
            new RemittancePayoutRecoveryService(
                remittances,
                new RemittanceClearingAccountService(accounts),
                walletCredit,
                transactionManager
            );

        RemittancePayoutRecoveryService.RecoveryResponse failed =
            recoveryService.recover(remittanceId, "provider-transfer-recovery-2");

        assertEquals(RemittanceEntity.Status.RECOVERY_REQUIRED, failed.status());
        assertEquals("RECOVERY_REQUIRED", failed.recoveryStatus());
        assertEquals(
            "RECOVERY_REQUIRED",
            jdbc.queryForObject(
                "SELECT status FROM remittances WHERE id = ?",
                String.class,
                remittanceId
            )
        );

        Mockito.reset(walletCredit);

        RemittancePayoutRecoveryService.RecoveryResponse retried =
            recoveryService.recover(remittanceId, "provider-transfer-recovery-2");

        assertEquals(RemittanceEntity.Status.FAILED, retried.status());
        assertEquals("RECOVERY_COMPLETED", retried.recoveryStatus());
        Mockito.verify(walletCredit, Mockito.times(1)).credit(
            Mockito.any(),
            Mockito.anyString(),
            Mockito.eq(walletId),
            Mockito.any(),
            Mockito.any()
        );
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRecoveryAllowsOnlyOneWalletRelease() throws Exception {
        WalletCreditService walletCredit = Mockito.mock(WalletCreditService.class);
        RemittancePayoutRecoveryService recoveryService =
            new RemittancePayoutRecoveryService(
                remittances,
                new RemittanceClearingAccountService(accounts),
                walletCredit,
                transactionManager
            );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RemittancePayoutRecoveryService.RecoveryResponse> first =
                executor.submit(() -> recoveryService.recover(
                    remittanceId, "provider-transfer-concurrent"
                ));
            Future<RemittancePayoutRecoveryService.RecoveryResponse> second =
                executor.submit(() -> recoveryService.recover(
                    remittanceId, "provider-transfer-concurrent"
                ));

            var firstResponse = first.get(10, TimeUnit.SECONDS);
            var secondResponse = second.get(10, TimeUnit.SECONDS);

            long completed = java.util.stream.Stream.of(firstResponse, secondResponse)
                .filter(response -> response.status() == RemittanceEntity.Status.FAILED)
                .count();

            assertEquals(2, completed);
            assertEquals(
                1,
                java.util.stream.Stream.of(firstResponse, secondResponse)
                    .filter(response -> "RECOVERY_COMPLETED".equals(response.recoveryStatus()))
                    .count()
            );
            assertEquals(
                1,
                java.util.stream.Stream.of(firstResponse, secondResponse)
                    .filter(response -> "ALREADY_RECOVERED".equals(response.recoveryStatus()))
                    .count()
            );

            Mockito.verify(walletCredit, Mockito.times(1)).credit(
                Mockito.any(),
                Mockito.anyString(),
                Mockito.eq(walletId),
                Mockito.any(),
                Mockito.any()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completedPayoutProducesBalancedPostgresLedgerAndIsIdempotent() {
        RemittancePayoutLedgerService service =
            new RemittancePayoutLedgerService(accounts, transactions, postings);

        RemittanceEntity remittance = Mockito.mock(RemittanceEntity.class);
        Mockito.when(remittance.getId()).thenReturn(remittanceId);
        Mockito.when(remittance.getClearingAccountId()).thenReturn(clearingId);
        Mockito.when(remittance.getSourceCurrency()).thenReturn(Currency.EUR);
        Mockito.when(remittance.getSourceAmount()).thenReturn(new BigDecimal("100.00"));
        Mockito.when(remittance.getFeeAmount()).thenReturn(new BigDecimal("2.50"));

        service.recordCompletedPayout(remittance, "FLUTTERWAVE", "fw-integration-1");
        service.recordCompletedPayout(remittance, "FLUTTERWAVE", "fw-integration-1");

        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM remittance_payout_ledger_postings",
            Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_transactions",
            Integer.class
        ));
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries",
            Integer.class
        ));

        BigDecimal debits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE direction = 'DEBIT'",
            BigDecimal.class
        );
        BigDecimal credits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE direction = 'CREDIT'",
            BigDecimal.class
        );

        assertEquals(0, debits.compareTo(new BigDecimal("102.50")));
        assertEquals(0, credits.compareTo(new BigDecimal("102.50")));

        assertEquals(
            "LIABILITY",
            jdbc.queryForObject(
                "SELECT account_type FROM ledger_accounts WHERE id = ?",
                String.class,
                clearingId
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_accounts WHERE name = 'RackPay Payout Settlement FLUTTERWAVE EUR'",
                Integer.class
            )
        );
    }
}
