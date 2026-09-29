package com.rackpay.api.auth.adapters.out.keycloak;

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
