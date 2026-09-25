package com.rackpay.api.persistence.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformAdminJpaRepository extends JpaRepository<PlatformAdminEntity, UUID> {
    boolean existsByUserId(UUID userId);
    boolean existsByUserIdAndBootstrapAdminTrue(UUID userId);
}
