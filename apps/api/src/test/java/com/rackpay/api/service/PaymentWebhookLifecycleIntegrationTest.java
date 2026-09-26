package com.rackpay.api.service;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.payment.PaymentProviderEventEntity;
import com.rackpay.api.payment.PaymentProvider;
import com.rackpay.api.payment.PaymentProviderRegistry;
import com.rackpay.api.payment.PaymentProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PaymentWebhookService.class,
    PaymentAdjustmentService.class,
    WalletBalanceService.class,
    WalletCreditService.class,
    WalletDebitService.class,
    WalletFinancialOperationService.class,
    WalletLedgerService.class,
    FinancialTransactionService.class,
    PaymentSettlementAccountService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentWebhookLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID WALLET_LEDGER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID WALLET_BALANCE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentWebhookService webhookService;

    @MockBean
    private PaymentProviderRegistry registry;

    @MockBean
    private PaymentProvider provider;

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
            VALUES (?, ?, ?, 'Payment', 'Tester', 'ACTIVE')
            """,
            USER_ID,
            "payment-webhook-test-subject",
            "payment-webhook-test@rackpay.local"
        );

        jdbc.update("""
            INSERT INTO ledger_accounts (
                id, name, currency_code, account_type, created_at
            )
            VALUES (?, ?, 'EUR', 'LIABILITY', CURRENT_TIMESTAMP)
            """,
            WALLET_LEDGER_ACCOUNT_ID,
            "Payment Webhook Wallet EUR"
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
            VALUES (?, ?, 'EUR', 0.00, ?, CURRENT_TIMESTAMP)
            """,
            WALLET_BALANCE_ID,
            WALLET_ID,
            WALLET_LEDGER_ACCOUNT_ID
        );

        jdbc.update("""
            INSERT INTO payment_transactions (
                id, user_id, wallet_id, provider, provider_payment_id,
                status, amount, currency_code, created_at, updated_at
            )
            VALUES (?, ?, ?, 'STRIPE', 'pi_test_123', 'PENDING', 50.00, 'EUR',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            PAYMENT_ID,
            USER_ID,
            WALLET_ID
        );

        when(registry.require(PaymentProviderType.STRIPE)).thenReturn(provider);
    }

    @Test
    void paidWebhookCreditsWalletExactlyOnce() {
        when(provider.handleWebhook("paid-event", Map.of())).thenReturn(
            webhook(
                "evt_paid_1",
                "pi_test_123",
                PaymentProvider.PaymentStatus.PAID,
                "checkout.session.completed",
                new BigDecimal("50.00"),
                Currency.EUR
            )
        );

        webhookService.handle(PaymentProviderType.STRIPE, "paid-event", Map.of());
        webhookService.handle(PaymentProviderType.STRIPE, "paid-event", Map.of());

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                WALLET_ID
            ).compareTo(new BigDecimal("50.00"))
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
            "PAID",
            jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE id = ?",
                String.class,
                PAYMENT_ID
            )
        );
        assertEquals(
            "PROCESSED",
            jdbc.queryForObject(
                "SELECT status FROM payment_provider_events WHERE provider = 'STRIPE' AND event_id = 'evt_paid_1'",
                String.class
            )
        );
    }

    @Test
    void paidWebhookRejectsAmountMismatchWithoutCreditingWallet() {
        when(provider.handleWebhook("bad-paid-event", Map.of())).thenReturn(
            webhook(
                "evt_bad_paid",
                "pi_test_123",
                PaymentProvider.PaymentStatus.PAID,
                "checkout.session.completed",
                new BigDecimal("49.99"),
                Currency.EUR
            )
        );

        assertThrows(
            SecurityException.class,
            () -> webhookService.handle(PaymentProviderType.STRIPE, "bad-paid-event", Map.of())
        );

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                WALLET_ID
            ).compareTo(BigDecimal.ZERO)
        );
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions",
                Integer.class
            )
        );
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_provider_events",
                Integer.class
            )
        );
    }

    @Test
    void refundWebhookReversesWalletCreditAndIsIdempotent() {
        when(provider.handleWebhook("paid-event", Map.of())).thenReturn(
            webhook(
                "evt_paid_2",
                "pi_test_123",
                PaymentProvider.PaymentStatus.PAID,
                "checkout.session.completed",
                new BigDecimal("50.00"),
                Currency.EUR
            )
        );
        when(provider.handleWebhook("refund-event", Map.of())).thenReturn(
            webhook(
                "evt_refund_1",
                "pi_test_123",
                PaymentProvider.PaymentStatus.REFUNDED,
                "charge.refunded",
                new BigDecimal("50.00"),
                Currency.EUR
            )
        );

        webhookService.handle(PaymentProviderType.STRIPE, "paid-event", Map.of());
        webhookService.handle(PaymentProviderType.STRIPE, "refund-event", Map.of());
        webhookService.handle(PaymentProviderType.STRIPE, "refund-event", Map.of());

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                WALLET_ID
            ).compareTo(BigDecimal.ZERO)
        );
        assertEquals(
            2,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE wallet_id = ? AND status = 'COMPLETED'",
                Integer.class,
                WALLET_ID
            )
        );
        assertEquals(
            2,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transactions",
                Integer.class
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_adjustments WHERE payment_transaction_id = ?",
                Integer.class,
                PAYMENT_ID
            )
        );
        assertEquals(
            "COMPLETED",
            jdbc.queryForObject(
                "SELECT status FROM payment_adjustments WHERE payment_transaction_id = ?",
                String.class,
                PAYMENT_ID
            )
        );
        assertEquals(
            "REFUNDED",
            jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE id = ?",
                String.class,
                PAYMENT_ID
            )
        );
    }

    @Test
    void refundWithWrongCurrencyCannotReverseWallet() {
        when(provider.handleWebhook("paid-event", Map.of())).thenReturn(
            webhook(
                "evt_paid_3",
                "pi_test_123",
                PaymentProvider.PaymentStatus.PAID,
                "checkout.session.completed",
                new BigDecimal("50.00"),
                Currency.EUR
            )
        );
        when(provider.handleWebhook("wrong-currency-refund", Map.of())).thenReturn(
            webhook(
                "evt_wrong_currency_refund",
                "pi_test_123",
                PaymentProvider.PaymentStatus.REFUNDED,
                "charge.refunded",
                new BigDecimal("50.00"),
                Currency.USD
            )
        );

        webhookService.handle(PaymentProviderType.STRIPE, "paid-event", Map.of());

        assertThrows(
            SecurityException.class,
            () -> webhookService.handle(
                PaymentProviderType.STRIPE,
                "wrong-currency-refund",
                Map.of()
            )
        );

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                WALLET_ID
            ).compareTo(new BigDecimal("50.00"))
        );
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_adjustments",
                Integer.class
            )
        );
    }

    private static PaymentProvider.WebhookResult webhook(
        String eventId,
        String paymentId,
        PaymentProvider.PaymentStatus status,
        String rawEventType,
        BigDecimal amount,
        Currency currency
    ) {
        return new PaymentProvider.WebhookResult(
            eventId,
            paymentId,
            status,
            rawEventType,
            "rackpay-payment-reference",
            amount,
            currency
        );
    }
}
