package com.rackpay.api.security;

import com.rackpay.api.persistence.admin.PlatformAdminJpaRepository;
import com.rackpay.api.persistence.user.UserEntity;
import com.rackpay.api.persistence.user.UserJpaRepository;
import com.rackpay.api.persistence.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminAuthorizationManagerTest {

    @Test
    void allowsRackPayAdminEvenWithoutKeycloakAdminRole() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "subject-admin");

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);

        when(users.findByKeycloakSubject("subject-admin")).thenReturn(Optional.of(user));
        when(admins.existsByUserId(userId)).thenReturn(true);

        var manager = new PlatformAdminAuthorizationManager(admins, users);
        var authentication = new TestingAuthenticationToken(
            "subject-admin",
            "credentials"
        );
        authentication.setAuthenticated(true);

        AuthorizationDecision decision = manager.check(
            () -> authentication,
            context("/api/v1/admin/admins")
        );

        assertTrue(decision.isGranted());
        verify(admins).existsByUserId(userId);
    }

    @Test
    void deniesKeycloakAdminRoleWithoutRackPayAdminRecord() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "subject-user");

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);

        when(users.findByKeycloakSubject("subject-user")).thenReturn(Optional.of(user));
        when(admins.existsByUserId(userId)).thenReturn(false);

        var authentication = new TestingAuthenticationToken(
            "subject-user",
            "credentials",
            "ROLE_ADMIN"
        );
        authentication.setAuthenticated(true);

        var manager = new PlatformAdminAuthorizationManager(admins, users);

        AuthorizationDecision decision = manager.check(
            () -> authentication,
            context("/api/v1/admin/payment-providers")
        );

        assertFalse(decision.isGranted());
    }

    @Test
    void deniesAuthenticatedKeycloakUserNotProvisionedInRackPay() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);

        when(users.findByKeycloakSubject("unknown-subject")).thenReturn(Optional.empty());

        var authentication = new TestingAuthenticationToken(
            "unknown-subject",
            "credentials",
            "ROLE_ADMIN"
        );
        authentication.setAuthenticated(true);

        var manager = new PlatformAdminAuthorizationManager(admins, users);

        AuthorizationDecision decision = manager.check(
            () -> authentication,
            context("/api/v1/admin/admins")
        );

        assertFalse(decision.isGranted());
        verifyNoInteractions(admins);
    }

    @Test
    void deniesUnauthenticatedRequest() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);

        var authentication = new TestingAuthenticationToken(
            "subject",
            "credentials"
        );
        authentication.setAuthenticated(false);

        var manager = new PlatformAdminAuthorizationManager(admins, users);

        AuthorizationDecision decision = manager.check(
            () -> authentication,
            context("/api/v1/admin/admins")
        );

        assertFalse(decision.isGranted());
        verifyNoInteractions(users, admins);
    }

    @Test
    void bootstrapFlagDoesNotChangeGeneralAdminAuthorization() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "subject-admin");

        UserJpaRepository users = mock(UserJpaRepository.class);
        PlatformAdminJpaRepository admins = mock(PlatformAdminJpaRepository.class);

        when(users.findByKeycloakSubject("subject-admin")).thenReturn(Optional.of(user));
        when(admins.existsByUserId(userId)).thenReturn(true);

        var manager = new PlatformAdminAuthorizationManager(admins, users);
        var authentication = new TestingAuthenticationToken("subject-admin", "credentials");
        authentication.setAuthenticated(true);

        AuthorizationDecision decision = manager.check(
            () -> authentication,
            context("/api/v1/admin/payment-providers")
        );

        assertTrue(decision.isGranted());
        verify(admins).existsByUserId(userId);
    }

    private static RequestAuthorizationContext context(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return new RequestAuthorizationContext(request);
    }

    private static UserEntity user(UUID id, String subject) {
        return new UserEntity(
            id,
            subject,
            subject + "@rackpay.local",
            "Test",
            "User",
            null,
            UserStatus.ACTIVE,
            Instant.now()
        );
    }
}
