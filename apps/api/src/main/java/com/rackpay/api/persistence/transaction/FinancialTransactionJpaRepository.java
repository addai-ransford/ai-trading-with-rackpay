package com.rackpay.api.persistence.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionJpaRepository extends JpaRepository<FinancialTransactionEntity, UUID> {
    Optional<FinancialTransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
