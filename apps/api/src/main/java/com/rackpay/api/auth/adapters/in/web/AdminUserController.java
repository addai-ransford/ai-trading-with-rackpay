package com.rackpay.api.auth.adapters.in.web;

import com.rackpay.api.auth.core.service.AdminCreateRequest;
import com.rackpay.api.auth.core.service.AdminResponse;
import com.rackpay.api.auth.core.service.AdminUserService;
import com.rackpay.api.payment.adapters.out.persistence.Status;
import com.rackpay.api.payment.adapters.out.providers.stripe.Response;

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
