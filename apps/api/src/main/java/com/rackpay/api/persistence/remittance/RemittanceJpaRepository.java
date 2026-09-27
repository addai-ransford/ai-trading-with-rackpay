package com.rackpay.api.persistence.remittance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RemittanceJpaRepository extends JpaRepository<RemittanceEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from RemittanceEntity r
        where r.id = :id
        """)
    Optional<RemittanceEntity> findByIdForUpdate(@Param("id") UUID id);
    
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

    java.util.List<RemittanceEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
