package com.rackpay.api.remittance;

import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import com.rackpay.api.persistence.ledger.LedgerTransactionJpaRepository;
import com.rackpay.api.persistence.remittance.RemittanceEntity;
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

    private UUID clearingId;
    private UUID remittanceId;

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
                ledger_entries,
                ledger_transactions,
                ledger_accounts
            CASCADE
            """);

        clearingId = UUID.randomUUID();
        remittanceId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO ledger_accounts
                (id, name, currency_code, account_type, created_at)
            VALUES (?, 'Integration Remittance Clearing EUR', 'EUR', 'LIABILITY', CURRENT_TIMESTAMP)
            """, clearingId);
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
