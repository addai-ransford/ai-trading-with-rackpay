package com.rackpay.api.persistence.payment;
import com.rackpay.api.payment.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentProviderEventJpaRepository extends JpaRepository<PaymentProviderEventEntity,UUID>{
    Optional<PaymentProviderEventEntity> findByProviderAndEventId(PaymentProviderType provider,String eventId);
}