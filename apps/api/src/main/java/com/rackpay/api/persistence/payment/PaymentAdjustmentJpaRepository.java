package com.rackpay.api.persistence.payment;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAdjustmentJpaRepository extends JpaRepository<PaymentAdjustmentEntity, UUID> {
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
