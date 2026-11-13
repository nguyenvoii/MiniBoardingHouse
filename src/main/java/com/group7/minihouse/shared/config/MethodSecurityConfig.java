package com.group7.minihouse.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Chi bat @PreAuthorize khi app.security.enabled=true.
 * Khi tat (mac dinh), @PreAuthorize bi bo qua de dong doi test API khong can token.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
public class MethodSecurityConfig {
}
