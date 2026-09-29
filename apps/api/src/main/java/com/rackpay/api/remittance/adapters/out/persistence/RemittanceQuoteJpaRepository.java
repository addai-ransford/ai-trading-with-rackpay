package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.payment.adapters.out.persistence.Type;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RemittanceQuoteJpaRepository extends JpaRepository<RemittanceQuoteEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from RemittanceQuoteEntity q where q.id = :id")
    Optional<RemittanceQuoteEntity> findByIdForUpdate(@Param("id") UUID id);
}
