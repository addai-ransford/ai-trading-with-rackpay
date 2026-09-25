package com.rackpay.api.auth;

import com.rackpay.api.persistence.admin.PlatformAdminJpaRepository;
import com.rackpay.api.persistence.user.UserEntity;
import com.rackpay.api.persistence.user.UserJpaRepository;
import com.rackpay.api.persistence.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {

    @Test
    void bootstrapAdminCanCreateAnotherAdmin() {
        UUID bootstrapId = UUID.randomUUID();
        UUID newAdminId = UUID.randomUUID();

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);
        RegistrationService registration = mock(RegistrationService.class);

        UserEntity bootstrap = user(bootstrapId, "bootstrap-subject", "bootstrap@rackpay.local");
        UserEntity created = user(newAdminId, "new-subject", "new@rackpay.local");

        when(users.findByKeycloakSubject("bootstrap-subject")).thenReturn(Optional.of(bootstrap));
        when(admins.existsByUserIdAndBootstrapAdminTrue(bootstrapId)).thenReturn(true);
        when(registration.register(any())).thenReturn(
            new RegistrationResponse(newAdminId, UUID.randomUUID(), "EUR")
        );
        when(users.findById(newAdminId)).thenReturn(Optional.of(created));

        var service = new AdminUserService(admins, users, registration);

        var result = service.createAdmin(
            auth("bootstrap-subject"),
            new AdminUserService.AdminCreateRequest(
                "new@rackpay.local",
                "New",
                "Admin",
                null,
                "strong-password-123"
            )
        );

        assertEquals(newAdminId, result.userId());
        assertEquals("new@rackpay.local", result.email());
        assertTrue(result.admin());
        assertFalse(result.bootstrapAdmin());

        verify(registration).register(any(RegistrationRequest.class));
        verify(admins).save(any());
    }

    @Test
    void ordinaryAdminCannotCreateAnotherAdmin() {
        UUID adminId = UUID.randomUUID();

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);
        RegistrationService registration = mock(RegistrationService.class);

        when(users.findByKeycloakSubject("admin-subject")).thenReturn(
            Optional.of(user(adminId, "admin-subject", "admin@rackpay.local"))
        );
        when(admins.existsByUserIdAndBootstrapAdminTrue(adminId)).thenReturn(false);

        var service = new AdminUserService(admins, users, registration);

        assertThrows(
            org.springframework.security.access.AccessDeniedException.class,
            () -> service.createAdmin(
                auth("admin-subject"),
                new AdminUserService.AdminCreateRequest(
                    "new@rackpay.local",
                    "New",
                    "Admin",
                    null,
                    "strong-password-123"
                )
            )
        );

        verifyNoInteractions(registration);
        verify(admins, never()).save(any());
    }

    @Test
    void nonAdminCannotCreateAnotherAdmin() {
        UUID userId = UUID.randomUUID();

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);
        RegistrationService registration = mock(RegistrationService.class);

        when(users.findByKeycloakSubject("user-subject")).thenReturn(
            Optional.of(user(userId, "user-subject", "user@rackpay.local"))
        );
        when(admins.existsByUserIdAndBootstrapAdminTrue(userId)).thenReturn(false);

        var service = new AdminUserService(admins, users, registration);

        assertThrows(
            org.springframework.security.access.AccessDeniedException.class,
            () -> service.createAdmin(
                auth("user-subject"),
                new AdminUserService.AdminCreateRequest(
                    "new@rackpay.local",
                    "New",
                    "Admin",
                    null,
                    "strong-password-123"
                )
            )
        );

        verifyNoInteractions(registration);
    }

    private static Authentication auth(String subject) {
        var authentication = new TestingAuthenticationToken(subject, "credentials");
        authentication.setAuthenticated(true);
        return authentication;
    }

    private static UserEntity user(UUID id, String subject, String email) {
        return new UserEntity(
            id,
            subject,
            email,
            "Test",
            "User",
            null,
            UserStatus.ACTIVE,
            Instant.now()
        );
    }
}
