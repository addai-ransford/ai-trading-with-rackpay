package com.rackpay.api.persistence.payment;
import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransactionEntity,UUID>{
 Optional<PaymentTransactionEntity> findByProviderAndProviderPaymentId(PaymentProviderType provider,String providerPaymentId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from PaymentTransactionEntity p where p.provider = :provider and p.providerPaymentId = :paymentId")
 Optional<PaymentTransactionEntity> findByProviderAndProviderPaymentIdForUpdate(@Param("provider") PaymentProviderType provider,@Param("paymentId") String paymentId);
}