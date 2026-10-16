package com.rackpay.api.remittance.adapters.out.persistence;

import com.rackpay.api.remittance.core.model.MobileMoneyNetwork;
import com.rackpay.api.shared.core.money.Money;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MobileMoneyNetworkJpaRepository extends JpaRepository<MobileMoneyNetworkEntity, UUID> {
    List<MobileMoneyNetworkEntity> findAllByCountryCodeIgnoreCaseAndEnabledTrue(String countryCode);
    boolean existsByCountryCodeIgnoreCaseAndCodeIgnoreCase(String countryCode, String code);
    Optional<MobileMoneyNetworkEntity> findByIdAndCountryCodeIgnoreCase(UUID id, String countryCode);
    Optional<MobileMoneyNetworkEntity> findByCountryCodeIgnoreCaseAndCodeIgnoreCaseAndEnabledTrue(
        String countryCode, String code
    );
}
