package com.rackpay.api.persistence.wallet;

import com.rackpay.api.domain.money.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {
    Optional<WalletEntity> findByOwnerIdAndCurrency(UUID ownerId, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(@Param("id") UUID id);
}
