package com.rackpay.api.auth;

import com.rackpay.api.persistence.admin.PlatformAdminEntity;
import com.rackpay.api.persistence.admin.PlatformAdminJpaRepository;
import com.rackpay.api.persistence.user.UserEntity;
import com.rackpay.api.persistence.user.UserJpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AdminUserService {
    private final PlatformAdminJpaRepository admins;
    private final UserJpaRepository users;
    private final RegistrationService registrationService;

    public AdminUserService(
        PlatformAdminJpaRepository admins,
        UserJpaRepository users,
        RegistrationService registrationService
    ) {
        this.admins = admins;
        this.users = users;
        this.registrationService = registrationService;
    }

    @Transactional(readOnly = true)
    public void requireBootstrapAdmin(Authentication authentication) {
        UserEntity user = users.findByKeycloakSubject(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("RackPay user is not provisioned"));

        if (!admins.existsByUserIdAndBootstrapAdminTrue(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Only the bootstrap administrator can create administrators"
            );
        }
    }

    @Transactional
    public AdminResponse createAdmin(Authentication authentication, AdminCreateRequest request) {
        requireBootstrapAdmin(authentication);

        RegistrationResponse registered = registrationService.register(
            new RegistrationRequest(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.phone(),
                request.password()
            )
        );

        admins.save(new PlatformAdminEntity(
            registered.userId(),
            false,
            Instant.now()
        ));

        UserEntity user = users.findById(registered.userId())
            .orElseThrow(() -> new IllegalStateException("Created RackPay admin user was not found"));

        return new AdminResponse(user.getId(), user.getEmail(), true, false);
    }

    public record AdminCreateRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Email
        @jakarta.validation.constraints.Size(max = 320)
        String email,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 100)
        String firstName,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 100)
        String lastName,

        @jakarta.validation.constraints.Size(max = 30)
        String phone,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 12, max = 128)
        String password
    ) {}

    public record AdminResponse(
        UUID userId,
        String email,
        boolean admin,
        boolean bootstrapAdmin
    ) {}
}
