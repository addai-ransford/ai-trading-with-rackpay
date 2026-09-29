package com.rackpay.api.shared.configuration;

import com.rackpay.api.auth.adapters.out.keycloak.BootstrapAdminProperties;
import com.rackpay.api.auth.adapters.out.keycloak.KeycloakAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    KeycloakAdminProperties.class,
    BootstrapAdminProperties.class
})
public class KeycloakConfig {}
