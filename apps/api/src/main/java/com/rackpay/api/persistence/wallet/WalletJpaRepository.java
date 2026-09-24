package com.rackpay.api.persistence.wallet;

import com.rackpay.api.domain.money.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {
    Optional<WalletEntity> findByOwnerIdAndCurrency(UUID ownerId, Currency currency);
}
