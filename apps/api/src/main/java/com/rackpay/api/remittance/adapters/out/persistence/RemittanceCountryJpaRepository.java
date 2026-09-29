package com.rackpay.api.remittance.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RemittanceCountryJpaRepository extends JpaRepository<RemittanceCountryEntity, String> {
    Optional<RemittanceCountryEntity> findByCodeIgnoreCaseAndEnabledTrue(String code);
}
