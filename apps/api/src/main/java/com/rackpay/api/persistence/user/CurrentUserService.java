package com.rackpay.api.persistence.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {
    private final UserJpaRepository users;

    public CurrentUserService(UserJpaRepository users) {
        this.users = users;
    }

    public UserEntity requireUser(Authentication authentication) {
        String subject = authentication.getName();
        return users.findByKeycloakSubject(subject)
            .orElseThrow(() -> new IllegalStateException("RackPay user is not provisioned"));
    }

    public UUID requireUserId(Authentication authentication) {
        return requireUser(authentication).getId();
    }
}
