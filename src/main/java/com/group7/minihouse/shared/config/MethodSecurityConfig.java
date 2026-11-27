package com.group7.minihouse.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Chi bat @PreAuthorize khi app.security.enabled=true.
 * Khi tat (chi may dev, dat trong application-local.properties), @PreAuthorize bi bo qua de test API khong can token.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
public class MethodSecurityConfig {
}
