package com.rackpay.api.auth;

import com.rackpay.api.persistence.admin.PlatformAdminEntity;
import com.rackpay.api.persistence.admin.PlatformAdminJpaRepository;
import com.rackpay.api.persistence.user.UserEntity;
import com.rackpay.api.persistence.user.UserJpaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class BootstrapAdminInitializer {
    @Bean
    ApplicationRunner bootstrapAdmin(
        BootstrapAdminProperties properties,
        UserJpaRepository users,
        PlatformAdminJpaRepository admins,
        RegistrationService registrationService
    ) {
        return args -> {
            if (!properties.enabled()) {
                return;
            }

            validate(properties);

            String email = properties.email().trim().toLowerCase();
            UserEntity existing = users.findAll().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

            if (existing != null) {
                if (!admins.existsByUserId(existing.getId())) {
                    if (admins.count() > 0) {
                        throw new IllegalStateException(
                            "Bootstrap admin email belongs to a non-admin RackPay user and another admin already exists"
                        );
                    }
                    admins.save(new PlatformAdminEntity(existing.getId(), true, Instant.now()));
                }
                return;
            }

            RegistrationResponse registered = registrationService.register(
                new RegistrationRequest(
                    email,
                    properties.firstName(),
                    properties.lastName(),
                    properties.phone(),
                    properties.password()
                )
            );

            if (admins.count() > 0) {
                throw new IllegalStateException(
                    "A platform admin already exists; bootstrap admin cannot be initialized"
                );
            }

            admins.save(new PlatformAdminEntity(registered.userId(), true, Instant.now()));
        };
    }

    private void validate(BootstrapAdminProperties properties) {
        if (properties.email() == null || properties.email().isBlank()
            || properties.firstName() == null || properties.firstName().isBlank()
            || properties.lastName() == null || properties.lastName().isBlank()
            || properties.password() == null || properties.password().length() < 12) {
            throw new IllegalStateException(
                "Bootstrap admin requires email, firstName, lastName and a password of at least 12 characters"
            );
        }
    }
}
