package com.rackpay.api.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByKeycloakSubject(String keycloakSubject);
    boolean existsByEmailIgnoreCase(String email);
}
