package com.rackpay.api.service;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.domain.money.Money;
import com.rackpay.api.domain.transaction.IdempotencyKey;
import com.rackpay.api.persistence.transaction.FinancialTransactionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    FinancialTransactionService.class,
    WalletFinancialOperationService.class,
    WalletLedgerService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletFinancialOperationConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID WALLET_LEDGER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID WALLET_BALANCE_ID = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WalletFinancialOperationService operations;

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
                payment_adjustments,
                payment_provider_events,
                payment_transactions,
                payment_settlement_accounts,
                financial_transactions,
                ledger_entries,
                ledger_transactions,
                wallet_balances,
                wallets,
                ledger_accounts,
                users
            CASCADE
            """);

        jdbc.update("""
            INSERT INTO users (
                id, keycloak_subject, email, first_name, last_name, status
            )
            VALUES (?, ?, ?, 'Test', 'User', 'ACTIVE')
            """,
            USER_ID,
            "concurrency-test-subject",
            "concurrency-test@rackpay.local"
        );

        jdbc.update("""
            INSERT INTO ledger_accounts (
                id, name, currency_code, account_type, created_at
            )
            VALUES (?, ?, 'EUR', 'LIABILITY', CURRENT_TIMESTAMP)
            """,
            WALLET_LEDGER_ACCOUNT_ID,
            "Concurrency Test Wallet EUR"
        );

        jdbc.update("""
            INSERT INTO ledger_accounts (
                id, name, currency_code, account_type, created_at
            )
            VALUES (?, ?, 'EUR', 'ASSET', CURRENT_TIMESTAMP)
            """,
            COUNTERPARTY_ACCOUNT_ID,
            "Concurrency Test Counterparty EUR"
        );

        jdbc.update("""
            INSERT INTO wallets (id, owner_id, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """,
            WALLET_ID,
            USER_ID
        );

        jdbc.update("""
            INSERT INTO wallet_balances (
                id, wallet_id, currency_code, balance, ledger_account_id, created_at
            )
            VALUES (?, ?, 'EUR', 100.00, ?, CURRENT_TIMESTAMP)
            """,
            WALLET_BALANCE_ID,
            WALLET_ID,
            WALLET_LEDGER_ACCOUNT_ID
        );
    }

    @Test
    void concurrentDifferentDebitsCannotOverspendWallet() throws Exception {
        int requestCount = 10;
        BigDecimal debitAmount = new BigDecimal("20.00");

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<FinancialTransactionEntity>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                final int requestNumber = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));

                    return operations.debitWallet(
                        new IdempotencyKey("concurrency-debit-" + requestNumber),
                        sha256For("concurrency-debit-" + requestNumber),
                        WALLET_ID,
                        COUNTERPARTY_ACCOUNT_ID,
                        new Money(debitAmount, Currency.EUR)
                    );
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successful = 0;
            int failed = 0;

            for (Future<FinancialTransactionEntity> future : futures) {
                try {
                    FinancialTransactionEntity transaction = future.get(30, TimeUnit.SECONDS);
                    assertEquals(
                        FinancialTransactionEntity.OperationType.DEBIT,
                        transaction.getOperationType()
                    );
                    successful++;
                } catch (Exception expected) {
                    failed++;
                }
            }

            assertEquals(5, successful);
            assertEquals(5, failed);
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                    BigDecimal.class,
                    WALLET_ID
                ).compareTo(BigDecimal.ZERO)
            );
            assertEquals(
                5,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE wallet_id = ? AND status = 'COMPLETED'",
                    Integer.class,
                    WALLET_ID
                )
            );
            assertEquals(
                5,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions",
                    Integer.class
                )
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesOnlyOneWalletOperation() throws Exception {
        int requestCount = 10;
        String idempotencyValue = "concurrent-same-request";
        String requestHash = sha256For(idempotencyValue);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<FinancialTransactionEntity>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));

                    return operations.debitWallet(
                        new IdempotencyKey(idempotencyValue),
                        requestHash,
                        WALLET_ID,
                        COUNTERPARTY_ACCOUNT_ID,
                        new Money(new BigDecimal("20.00"), Currency.EUR)
                    );
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<FinancialTransactionEntity> results = new ArrayList<>();
            for (Future<FinancialTransactionEntity> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            UUID transactionId = results.getFirst().getId();

            assertTrue(results.stream().allMatch(t -> transactionId.equals(t.getId())));
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE idempotency_key = ?",
                    Integer.class,
                    idempotencyValue
                )
            );
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE wallet_id = ? AND status = 'COMPLETED'",
                    Integer.class,
                    WALLET_ID
                )
            );
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions",
                    Integer.class
                )
            );
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                    BigDecimal.class,
                    WALLET_ID
                ).compareTo(new BigDecimal("80.00"))
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static String sha256For(String value) {
        return "a".repeat(64);
    }
}
