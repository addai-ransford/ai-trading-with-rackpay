package com.rackpay.api.persistence.transaction;

import com.rackpay.api.domain.transaction.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionJpaRepository extends JpaRepository<FinancialTransactionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FinancialTransactionEntity t where t.id = :id")
    Optional<FinancialTransactionEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FinancialTransactionEntity t where t.idempotencyKey = :key")
    Optional<FinancialTransactionEntity> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Query(value = """
        select pg_advisory_xact_lock(hashtextextended(cast(:key as text), 0))
        """, nativeQuery = true)
    void lockIdempotencyKey(@Param("key") String key);

    Page<FinancialTransactionEntity> findAllByWalletIdOrderByCreatedAtDesc(
        UUID walletId,
        Pageable pageable
    );

    Optional<FinancialTransactionEntity> findByIdAndWalletId(
        UUID id,
        UUID walletId
    );
}
