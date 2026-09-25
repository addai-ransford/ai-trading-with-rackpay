package com.rackpay.api.persistence.payment;
import com.rackpay.api.payment.PaymentProviderType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PaymentProviderEventJpaRepository extends JpaRepository<PaymentProviderEventEntity,UUID>{
 Optional<PaymentProviderEventEntity> findByProviderAndEventId(PaymentProviderType provider,String eventId);
 @Query(value="select pg_advisory_xact_lock(hashtextextended(cast(:key as text),0))",nativeQuery=true)
 void lockEvent(@Param("key") String key);
}