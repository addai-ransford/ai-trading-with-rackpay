package com.rackpay.api.remittance.adapters.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RemittancePayoutAttemptJpaRepository extends JpaRepository<RemittancePayoutAttemptEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from RemittancePayoutAttemptEntity a
        where a.remittanceId = :remittanceId
        order by a.attemptNumber desc
        """)
    List<RemittancePayoutAttemptEntity> findAllByRemittanceIdForUpdate(@Param("remittanceId") UUID remittanceId);

    List<RemittancePayoutAttemptEntity> findAllByRemittanceIdOrderByAttemptNumberAsc(UUID remittanceId);
}
