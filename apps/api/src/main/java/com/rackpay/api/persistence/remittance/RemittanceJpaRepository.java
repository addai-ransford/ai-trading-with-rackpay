package com.rackpay.api.persistence.remittance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RemittanceJpaRepository extends JpaRepository<RemittanceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from RemittanceEntity r
        where r.userId = :userId
          and r.idempotencyKey = :idempotencyKey
        """)
    Optional<RemittanceEntity> findByUserIdAndIdempotencyKeyForUpdate(
        @Param("userId") UUID userId,
        @Param("idempotencyKey") String idempotencyKey
    );

    Optional<RemittanceEntity> findByUserIdAndId(UUID userId, UUID id);
}
