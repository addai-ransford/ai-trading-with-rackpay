package com.rackpay.api.persistence.payment;

import com.rackpay.api.payment.PaymentProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PaymentProviderConfigJpaRepository extends JpaRepository<PaymentProviderConfigEntity,UUID> {
    Optional<PaymentProviderConfigEntity> findByProvider(PaymentProviderType provider);
    Optional<PaymentProviderConfigEntity> findByActiveTrue();
    List<PaymentProviderConfigEntity> findAllByOrderByProviderAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentProviderConfigEntity p where p.id = :id")
    Optional<PaymentProviderConfigEntity> findByIdForUpdate(@Param("id") UUID id);
}
