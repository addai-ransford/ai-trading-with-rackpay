package com.rackpay.api.remittance.core.service;

import com.rackpay.api.ledger.core.model.LedgerAccount;
import com.rackpay.api.ledger.core.model.LedgerEntry;
import com.rackpay.api.ledger.core.model.LedgerTransaction;
import com.rackpay.api.ledger.core.model.EntryDirection;
import com.rackpay.api.ledger.core.model.LedgerAccountType;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountJpaRepository;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerEntryEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerTransactionEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerTransactionJpaRepository;
import com.rackpay.api.remittance.adapters.out.persistence.RemittanceEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittancePayoutLedgerPostingEntity;
import com.rackpay.api.remittance.adapters.out.persistence.RemittancePayoutLedgerPostingJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class RemittancePayoutLedgerService {
    private static final String SETTLEMENT_PREFIX = "RackPay Payout Settlement ";

    private final LedgerAccountJpaRepository ledgerAccounts;
    private final LedgerTransactionJpaRepository ledgerTransactions;
    private final RemittancePayoutLedgerPostingJpaRepository postings;

    public RemittancePayoutLedgerService(
        LedgerAccountJpaRepository ledgerAccounts,
        LedgerTransactionJpaRepository ledgerTransactions,
        RemittancePayoutLedgerPostingJpaRepository postings
    ) {
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerTransactions = ledgerTransactions;
        this.postings = postings;
    }

    @Transactional
    public void recordCompletedPayout(
        RemittanceEntity remittance,
        String provider,
        String providerTransferId
    ) {
        if (postings.findByRemittanceId(remittance.getId()).isPresent()) return;

        if (remittance.getClearingAccountId() == null) {
            throw new IllegalStateException("remittance has no clearing account");
        }

        Currency currency = remittance.getSourceCurrency();
        LedgerAccountEntity clearing = ledgerAccounts.findById(remittance.getClearingAccountId())
            .orElseThrow(() -> new IllegalStateException("remittance clearing account not found"));

        if (clearing.getCurrency() != currency) {
            throw new IllegalStateException("remittance clearing account currency mismatch");
        }

        LedgerAccountEntity settlement = settlementAccount(provider, currency);
        Instant now = Instant.now();
        LedgerTransactionEntity transaction = new LedgerTransactionEntity(UUID.randomUUID(), now);

        // The remittance clearing liability is reduced when the provider has paid
        // the beneficiary. The provider settlement asset is reduced because cash
        // has left the provider balance.
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), clearing, remittance.getSourceAmount().add(remittance.getFeeAmount()),
            currency, EntryDirection.DEBIT, now
        ));
        transaction.addEntry(new LedgerEntryEntity(
            UUID.randomUUID(), settlement, remittance.getSourceAmount().add(remittance.getFeeAmount()),
            currency, EntryDirection.CREDIT, now
        ));

        ledgerTransactions.save(transaction);

        postings.save(new RemittancePayoutLedgerPostingEntity(
            UUID.randomUUID(),
            remittance.getId(),
            provider,
            providerTransferId,
            remittance.getSourceAmount().add(remittance.getFeeAmount()),
            currency,
            clearing.getId(),
            settlement.getId(),
            transaction.getId(),
            now
        ));
    }

    private LedgerAccountEntity settlementAccount(String provider, Currency currency) {
        String name = SETTLEMENT_PREFIX + provider + " " + currency.name();
        return ledgerAccounts.findByNameAndCurrency(name, currency)
            .orElseGet(() -> ledgerAccounts.saveAndFlush(new LedgerAccountEntity(
                UUID.nameUUIDFromBytes(
                    ("rackpay:payout:settlement:" + provider + ":" + currency.name())
                        .getBytes(StandardCharsets.UTF_8)
                ),
                name,
                currency,
                LedgerAccountType.ASSET,
                Instant.now()
            )));
    }
}