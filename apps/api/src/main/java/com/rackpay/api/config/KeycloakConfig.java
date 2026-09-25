package com.rackpay.api.config;

import com.rackpay.api.auth.BootstrapAdminProperties;
import com.rackpay.api.auth.KeycloakAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    KeycloakAdminProperties.class,
    BootstrapAdminProperties.class
})
public class KeycloakConfig {}
