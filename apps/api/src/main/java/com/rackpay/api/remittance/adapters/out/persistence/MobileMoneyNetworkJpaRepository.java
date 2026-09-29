package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.remittance.core.model.MobileMoneyNetwork;
import com.rackpay.api.shared.core.money.Money;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MobileMoneyNetworkJpaRepository extends JpaRepository<MobileMoneyNetworkEntity, UUID> {
    Optional<MobileMoneyNetworkEntity> findByCountryCodeIgnoreCaseAndCodeIgnoreCaseAndEnabledTrue(
        String countryCode, String code
    );
}
