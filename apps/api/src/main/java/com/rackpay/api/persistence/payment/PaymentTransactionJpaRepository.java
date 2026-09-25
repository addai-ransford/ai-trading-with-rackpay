package com.rackpay.api.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransactionEntity,UUID> {
    Optional<PaymentTransactionEntity> findByProviderAndProviderPaymentId(com.rackpay.api.payment.PaymentProviderType provider,String providerPaymentId);
}
