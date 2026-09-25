package com.rackpay.api.service;

import com.rackpay.api.domain.ledger.LedgerAccountType;
import com.rackpay.api.domain.money.Currency;
import com.rackpay.api.payment.PaymentProviderType;
import com.rackpay.api.persistence.ledger.*;
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
