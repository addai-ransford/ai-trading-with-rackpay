package com.rackpay.api.persistence.remittance;

import com.rackpay.api.remittance.PayoutMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RemittanceRecipientJpaRepository extends JpaRepository<RemittanceRecipientEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from RemittanceRecipientEntity r
        where r.userId = :userId
          and upper(r.countryCode) = upper(:countryCode)
          and r.payoutMethod = :payoutMethod
          and r.normalizedPhoneNumber = :normalizedPhoneNumber
          and r.mobileMoneyNetworkId = :networkId
          and r.active = true
    """)
    Optional<RemittanceRecipientEntity> findActiveMobileRecipientForUpdate(
        @Param("userId") UUID userId,
        @Param("countryCode") String countryCode,
        @Param("payoutMethod") PayoutMethod payoutMethod,
        @Param("normalizedPhoneNumber") String normalizedPhoneNumber,
        @Param("networkId") UUID networkId
    );
}
