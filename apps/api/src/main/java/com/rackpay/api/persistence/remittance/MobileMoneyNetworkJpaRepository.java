package com.rackpay.api.persistence.remittance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MobileMoneyNetworkJpaRepository extends JpaRepository<MobileMoneyNetworkEntity, UUID> {
    Optional<MobileMoneyNetworkEntity> findByCountryCodeIgnoreCaseAndCodeIgnoreCaseAndEnabledTrue(
        String countryCode, String code
    );
}
