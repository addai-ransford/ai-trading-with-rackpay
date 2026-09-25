package com.rackpay.api.persistence.wallet;

import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface WalletBalanceJpaRepository extends JpaRepository<WalletBalanceEntity, UUID> {
    Optional<WalletBalanceEntity> findByWalletIdAndCurrency(UUID walletId, Currency currency);

    List<WalletBalanceEntity> findAllByWalletIdOrderByCurrency(UUID walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select b
        from WalletBalanceEntity b
        where b.walletId = :walletId
          and b.currency = :currency
        """)
    Optional<WalletBalanceEntity> findByWalletIdAndCurrencyForUpdate(
        @Param("walletId") UUID walletId,
        @Param("currency") Currency currency
    );
}
