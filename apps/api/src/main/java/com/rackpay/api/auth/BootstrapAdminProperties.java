package com.rackpay.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rackpay.bootstrap-admin")
public record BootstrapAdminProperties(
    boolean enabled,
    String email,
    String firstName,
    String lastName,
    String phone,
    String password
) {}
