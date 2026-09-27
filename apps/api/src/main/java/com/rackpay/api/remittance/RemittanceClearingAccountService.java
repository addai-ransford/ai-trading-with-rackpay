package com.rackpay.api.remittance;

import com.rackpay.api.domain.ledger.LedgerAccountType;
import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.persistence.ledger.LedgerAccountEntity;
import com.rackpay.api.persistence.ledger.LedgerAccountJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RemittanceClearingAccountService {
    private static final String ACCOUNT_PREFIX = "RackPay Remittance Clearing ";

    private final LedgerAccountJpaRepository ledgerAccounts;

    public RemittanceClearingAccountService(LedgerAccountJpaRepository ledgerAccounts) {
        this.ledgerAccounts = ledgerAccounts;
    }

    @Transactional
    public LedgerAccountEntity require(Currency currency) {
        String name = ACCOUNT_PREFIX + currency.name();
        return ledgerAccounts.findByNameAndCurrency(name, currency)
            .orElseGet(() -> ledgerAccounts.saveAndFlush(new LedgerAccountEntity(
                UUID.nameUUIDFromBytes(("rackpay:remittance:clearing:" + currency.name())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name,
                currency,
                LedgerAccountType.LIABILITY,
                Instant.now()
            )));
    }
}
