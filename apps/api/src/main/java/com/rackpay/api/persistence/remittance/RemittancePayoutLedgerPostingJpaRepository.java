package com.rackpay.api.persistence.remittance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemittancePayoutLedgerPostingJpaRepository
    extends JpaRepository<RemittancePayoutLedgerPostingEntity, UUID> {

    Optional<RemittancePayoutLedgerPostingEntity> findByRemittanceId(UUID remittanceId);
}