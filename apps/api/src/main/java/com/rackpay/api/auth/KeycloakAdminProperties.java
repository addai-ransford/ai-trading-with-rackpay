package com.rackpay.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rackpay.keycloak.admin")
public record KeycloakAdminProperties(
    String baseUrl,
    String realm,
    String clientId,
    String clientSecret
) {}
