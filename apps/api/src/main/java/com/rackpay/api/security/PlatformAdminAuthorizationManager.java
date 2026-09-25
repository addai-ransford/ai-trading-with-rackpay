package com.rackpay.api.security;

import com.rackpay.api.persistence.admin.PlatformAdminJpaRepository;
import com.rackpay.api.persistence.user.UserJpaRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class PlatformAdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final PlatformAdminJpaRepository admins;
    private final UserJpaRepository users;

    public PlatformAdminAuthorizationManager(PlatformAdminJpaRepository admins, UserJpaRepository users) {
        this.admins = admins;
        this.users = users;
    }

    @Override
    public AuthorizationDecision check(
        Supplier<Authentication> authentication,
        RequestAuthorizationContext context
    ) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        return users.findByKeycloakSubject(current.getName())
            .map(user -> new AuthorizationDecision(admins.existsByUserId(user.getId())))
            .orElseGet(() -> new AuthorizationDecision(false));
    }
}
