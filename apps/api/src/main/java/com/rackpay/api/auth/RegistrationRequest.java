package com.rackpay.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 30) String phone,
    @NotBlank @Size(min = 12, max = 128) String password
) {}
