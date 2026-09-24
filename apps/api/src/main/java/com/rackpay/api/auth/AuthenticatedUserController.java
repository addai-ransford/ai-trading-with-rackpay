package com.rackpay.api.auth;

import com.rackpay.api.persistence.user.CurrentUserService;
import com.rackpay.api.persistence.user.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthenticatedUserController {
    private final CurrentUserService currentUser;

    public AuthenticatedUserController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        UserEntity user = currentUser.requireUser(authentication);
        return new MeResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }

    public record MeResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName
    ) {}
}
