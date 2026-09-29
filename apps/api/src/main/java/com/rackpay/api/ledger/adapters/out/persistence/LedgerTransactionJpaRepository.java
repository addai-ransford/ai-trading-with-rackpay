package com.rackpay.api.ledger.adapters.out.persistence;

import com.rackpay.api.ledger.core.model.LedgerTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionJpaRepository extends JpaRepository<LedgerTransactionEntity, UUID> {}
