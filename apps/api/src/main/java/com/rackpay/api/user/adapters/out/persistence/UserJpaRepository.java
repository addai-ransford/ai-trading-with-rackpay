package com.rackpay.api.user.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByKeycloakSubject(String keycloakSubject);
    boolean existsByEmailIgnoreCase(String email);
}
