package com.group7.minihouse.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.JwtAuthFilter;
import com.group7.minihouse.shared.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Bao mat: dang nhap JWT, khong luu session. Cong tac: app.security.enabled
 * - false (MAC DINH, che do dev): moi API mo, khong can token; @PreAuthorize bi bo qua (xem MethodSecurityConfig).
 * - true (demo/bao ve): chi cong khai POST /api/auth/login, GET /api/ping, Swagger; con lai phai gui header
 *   "Authorization: Bearer <token>". Bat bang app.security.enabled=true trong application-local.properties.
 *   Luat URL: /api/auth/** moi nguoi da dang nhap; /api/me/** chi nguoi thue (TENANT); moi URL khac chi nhan vien
 *   (LANDLORD, MANAGER, CASHIER, METER_READER). API cua nguoi thue PHAI nam duoi /api/me/**.
 * - Phan quyen theo vai tro (thu hep trong nhom nhan vien): gan @PreAuthorize("hasRole('MANAGER')") hoac
 *   hasAnyRole('LANDLORD','MANAGER') tren ham controller/service.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, ObjectMapper mapper,
                                    @Value("${app.security.enabled:false}") boolean enabled) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (enabled) {
                    auth.requestMatchers("/api/auth/login", "/api/ping",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        // Khu vuc nguoi thue: API cua nguoi thue DAT DUOI /api/me/** (vd /api/me/invoices)
                        .requestMatchers("/api/me/**").hasRole("TENANT")
                        // Mac dinh chi nhan vien: nguoi thue khong vao duoc API nao quen gan @PreAuthorize
                        .anyRequest().hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "METER_READER");
                } else {
                    auth.anyRequest().permitAll(); // che do dev: mo, khong can token
                }
            })
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, mapper, HttpStatus.UNAUTHORIZED, "Chua dang nhap hoac token het han"))
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, mapper, HttpStatus.FORBIDDEN, "Khong co quyen thuc hien")))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS cho frontend chay rieng o localhost (vi du React/Vite cong 3000, 5173). */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ApiResponse.error(message));
    }
}
