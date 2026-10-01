package com.rackpay.api.payment.adapters.out.persistence;

import com.rackpay.api.shared.core.transaction.TransactionId;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAdjustmentJpaRepository extends JpaRepository<PaymentAdjustmentEntity, UUID> {
    java.util.List<PaymentAdjustmentEntity> findAllByStatus(PaymentAdjustmentEntity.Status status);

    Optional<PaymentAdjustmentEntity> findByPaymentTransactionIdAndAdjustmentType(
        UUID paymentTransactionId,
        PaymentAdjustmentEntity.Type adjustmentType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from PaymentAdjustmentEntity a
        where a.paymentTransactionId = :paymentTransactionId
          and a.adjustmentType = :adjustmentType
        """)
    Optional<PaymentAdjustmentEntity> findByPaymentTransactionIdAndAdjustmentTypeForUpdate(
        @Param("paymentTransactionId") UUID paymentTransactionId,
        @Param("adjustmentType") PaymentAdjustmentEntity.Type adjustmentType
    );
}
