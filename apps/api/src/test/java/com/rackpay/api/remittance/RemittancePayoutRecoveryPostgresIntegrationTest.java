package com.rackpay.api.remittance;

import com.rackpay.api.remittance.core.model.*;
import com.rackpay.api.remittance.core.service.*;
import com.rackpay.api.remittance.ports.out.*;

import com.rackpay.api.remittance.core.service.*;

import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceJpaRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    RemittancePayoutRecoveryService.class,
    RemittanceClearingAccountService.class,
    com.rackpay.api.wallet.core.service.WalletCreditService.class,
    com.rackpay.api.wallet.core.service.WalletFinancialOperationService.class,
    com.rackpay.api.shared.core.service.FinancialTransactionService.class,
    com.rackpay.api.wallet.core.service.WalletLedgerService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RemittancePayoutRecoveryPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired RemittanceJpaRepository remittances;
    @Autowired RemittancePayoutRecoveryService recovery;

    private UUID remittanceId;
    private UUID userId;
    private UUID walletId;
    private UUID corridorId;
    private UUID recipientId;
    private UUID networkId;
    private UUID walletLedgerAccountId;

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
                financial_transactions,
                remittance_payout_ledger_postings,
                remittance_payout_attempts,
                remittances,
                remittance_recipients,
                mobile_money_networks,
                remittance_corridors,
                remittance_countries,
                wallet_balances,
                wallets,
                users,
                ledger_entries,
                ledger_transactions,
                ledger_accounts
            CASCADE
            """);

        remittanceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        corridorId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        networkId = UUID.randomUUID();
        walletLedgerAccountId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO users (id, keycloak_subject, email, first_name, last_name, status)
            VALUES (?, ?, ?, 'Recovery', 'Tester', 'ACTIVE')
            """, userId, "recovery-test-" + userId, "recovery-" + userId + "@rackpay.local");

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
                    '+233241234567', 'Recovery Recipient', TRUE)
            """, recipientId, userId, networkId);

        jdbc.update("""
            INSERT INTO remittances
                (id, user_id, wallet_id, corridor_id, recipient_id,
                 source_amount, source_currency_code, destination_amount,
                 destination_currency_code, fee_amount, fee_currency_code,
                 fx_rate, status, idempotency_key)
            VALUES (?, ?, ?, ?, ?, 100, 'EUR', 1250, 'GHS', 2.50, 'EUR',
                    12.5, 'RECOVERY_REQUIRED', 'recovery-integration')
            """, remittanceId, userId, walletId, corridorId, recipientId);

        jdbc.update("""
            INSERT INTO ledger_accounts
                (id, name, currency_code, account_type, created_at)
            VALUES (?, 'Recovery Wallet EUR', 'EUR', 'LIABILITY', CURRENT_TIMESTAMP)
            """, walletLedgerAccountId);
    }

    @Test
    void failedRecoveryPersistsRecoveryRequiredAndRetryIsIdempotent() {
        RemittancePayoutRecoveryService.RecoveryResponse first =
            recovery.recover(remittanceId, "provider-transfer-1");

        assertEquals(RemittanceEntity.Status.RECOVERY_REQUIRED, first.status());
        assertEquals("RECOVERY_REQUIRED", first.recoveryStatus());

        assertEquals(
            RemittanceEntity.Status.RECOVERY_REQUIRED,
            remittances.findById(remittanceId).orElseThrow().getStatus()
        );
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions",
                Integer.class
            )
        );

        jdbc.update("""
            INSERT INTO wallet_balances
                (id, wallet_id, currency_code, balance, ledger_account_id, created_at)
            VALUES (?, ?, 'EUR', 0, ?, CURRENT_TIMESTAMP)
            """, UUID.randomUUID(), walletId, walletLedgerAccountId);

        RemittancePayoutRecoveryService.RecoveryResponse second =
            recovery.recover(remittanceId, "provider-transfer-1");

        assertEquals(RemittanceEntity.Status.FAILED, second.status());
        assertEquals("RECOVERY_COMPLETED", second.recoveryStatus());

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                walletId
            ).compareTo(new BigDecimal("102.50"))
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions",
                Integer.class
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
            2,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries",
                Integer.class
            )
        );

        RemittancePayoutRecoveryService.RecoveryResponse third =
            recovery.recover(remittanceId, "provider-transfer-1");

        assertEquals(RemittanceEntity.Status.FAILED, third.status());
        assertEquals("ALREADY_RECOVERED", third.recoveryStatus());

        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions",
                Integer.class
            )
        );
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT balance FROM wallet_balances WHERE wallet_id = ? AND currency_code = 'EUR'",
                BigDecimal.class,
                walletId
            ).compareTo(new BigDecimal("102.50"))
        );
    }
}
