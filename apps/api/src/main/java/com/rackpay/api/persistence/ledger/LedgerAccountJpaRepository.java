package com.rackpay.api.persistence.ledger;

import com.rackpay.api.domain.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccountEntity, UUID> {
    Optional<LedgerAccountEntity> findByNameAndCurrency(String name, Currency currency);
}
