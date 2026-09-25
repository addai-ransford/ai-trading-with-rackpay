package com.rackpay.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/admins")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.AdminResponse create(
        Authentication authentication,
        @Valid @RequestBody AdminUserService.AdminCreateRequest request
    ) {
        return service.createAdmin(authentication, request);
    }
}
