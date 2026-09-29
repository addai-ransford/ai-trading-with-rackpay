package com.rackpay.api.payment.core.service;

import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountEntity;
import com.rackpay.api.ledger.adapters.out.persistence.LedgerAccountJpaRepository;
import com.rackpay.api.ledger.core.model.LedgerAccount;
import com.rackpay.api.payment.adapters.out.persistence.Type;
import com.rackpay.api.payment.ports.out.PaymentProvider;

import com.rackpay.api.ledger.core.model.LedgerAccountType;
import com.rackpay.api.shared.core.money.Currency;
import com.rackpay.api.payment.core.model.PaymentProviderType;
import com.rackpay.api.ledger.adapters.out.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentSettlementAccountService {
    private final LedgerAccountJpaRepository accounts;
    public PaymentSettlementAccountService(LedgerAccountJpaRepository accounts){this.accounts=accounts;}

    @Transactional
    public UUID requireAccount(PaymentProviderType provider,Currency currency){
        String name="Payment Clearing "+provider+" "+currency.name();
        return accounts.findByNameAndCurrency(name,currency)
            .map(LedgerAccountEntity::getId)
            .orElseGet(()->accounts.save(new LedgerAccountEntity(UUID.randomUUID(),name,currency,LedgerAccountType.ASSET,Instant.now())).getId());
    }
}
