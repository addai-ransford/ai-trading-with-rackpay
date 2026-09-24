package com.rackpay.api.persistence.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionJpaRepository extends JpaRepository<LedgerTransactionEntity, UUID> {}
