package com.rackpay.api.ledger.adapters.out.persistence;

import com.rackpay.api.ledger.core.model.LedgerAccount;

import com.rackpay.api.shared.core.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccountEntity, UUID> {
    Optional<LedgerAccountEntity> findByNameAndCurrency(String name, Currency currency);
}
