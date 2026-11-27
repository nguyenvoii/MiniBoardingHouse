package com.group7.minihouse.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.JwtAuthFilter;
import com.group7.minihouse.shared.security.JwtService;
import com.group7.minihouse.shared.security.UserStatusChecker;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
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
 * - true (MAC DINH, an toan): chi cong khai POST /api/auth/login, GET /api/ping, Swagger; con lai phai gui header
 *   "Authorization: Bearer <token>". Thieu cau hinh thi van an toan; app.jwt.secret bat buoc (xem SecurityStartupCheck).
 * - false (CHI may dev, dat app.security.enabled=false trong application-local.properties): moi API mo, khong can token;
 *   @PreAuthorize bi bo qua (xem MethodSecurityConfig).
 *   /api/me/** chi nguoi thue. Mac dinh API quan tri chi danh cho LANDLORD va MANAGER.
 *   CASHIER va METER_READER chi vao cac duong dan duoc liet ke theo nghiep vu.
 */
@Configuration
public class SecurityConfig {
    private static final String ID = "{id:[0-9]+}";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserStatusChecker statusChecker,
                                    ObjectMapper mapper, @Value("${app.security.enabled:true}") boolean enabled) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/verify-email",
                        "/api/auth/resend-verification").denyAll();
                if (enabled) {
                    auth.requestMatchers("/api/auth/login", "/api/auth/forgot-password",
                            "/api/auth/reset-password", "/api/auth/qr-login", "/api/ping",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                            "/ui/**", "/", "/index.html", "/assets/**", "/favicon.ico", "/og-image.png").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/rooms/**").permitAll()
                        // cong thanh toan goi ve (tu kiem tra bi mat qua header X-Callback-Secret trong controller)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/payments/callback").permitAll() // /ui/**: giao dien tinh xem thu (chi co tren may nao dat thu muc demo-ui)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/invoices/share/*").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        // Khu vuc nguoi thue: API cua nguoi thue DAT DUOI /api/me/** (vd /api/me/invoices)
                        .requestMatchers("/api/me/**").hasRole("TENANT")
                        // Xem chi tiet hoa don: nguoi thue duoc vao nhung InvoiceController chi tra hoa don cua chinh ho
                        .requestMatchers(HttpMethod.GET, "/api/contracts/signatures/*")
                        .hasAnyRole("LANDLORD", "MANAGER", "TENANT")
                        .requestMatchers(HttpMethod.GET, "/api/invoices/" + ID + "/detail", "/api/bank-accounts/qr/*")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "TENANT")
                        .requestMatchers(HttpMethod.GET, "/api/chi/properties", "/api/chi/properties/default",
                                "/api/chi/rooms", "/api/chi/rooms/search", "/api/chi/rooms/floor/" + ID,
                                "/api/chi/rooms/" + ID + "/photos")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "METER_READER")
                        .requestMatchers(HttpMethod.GET, "/api/notifications", "/api/notifications/unread-count")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "METER_READER")
                        .requestMatchers(HttpMethod.PATCH, "/api/notifications/" + ID + "/read", "/api/notifications/read-all")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "METER_READER")
                        .requestMatchers(HttpMethod.DELETE, "/api/notifications/read", "/api/notifications/" + ID)
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER", "METER_READER")
                        .requestMatchers(HttpMethod.GET, "/api/meter-periods", "/api/meter-periods/" + ID + "/readings",
                                "/api/meter-periods/" + ID + "/missing-rooms", "/api/meter-readings/" + ID,
                                "/api/meter-readings/" + ID + "/photos", "/api/meter-readings/" + ID + "/adjustments",
                                "/api/meter-photos/*", "/api/meter-alerts", "/api/rooms/" + ID + "/consumption",
                                "/api/properties/" + ID + "/consumption-comparison")
                        .hasAnyRole("LANDLORD", "MANAGER", "METER_READER")
                        .requestMatchers(HttpMethod.POST, "/api/meter-readings", "/api/meter-readings/" + ID + "/photos",
                                "/api/meter-readings/move-in", "/api/meter-readings/move-out")
                        .hasAnyRole("LANDLORD", "MANAGER", "METER_READER")
                        .requestMatchers(HttpMethod.GET, "/api/invoices", "/api/invoices/payable",
                                "/api/invoices/" + ID, "/api/invoices/" + ID + "/print", "/api/invoices/" + ID + "/notifications",
                                "/api/invoices/" + ID + "/partial-payments", "/api/invoices/summary",
                                "/api/invoices/summary/{month:[0-9]{4}-[0-9]{2}}",
                                "/api/payments", "/api/payments/" + ID, "/api/receipts",
                                "/api/receipts/" + ID, "/api/receipts/" + ID + "/print", "/api/tenants/" + ID + "/prepayment",
                                "/api/tenants/" + ID + "/prepayment/deductions", "/api/debts", "/api/debts/" + ID,
                                "/api/debts/summary", "/api/debts/export", "/api/debts/" + ID + "/processing-logs",
                                "/api/billing/periods", "/api/reports/revenue",
                                "/api/reports/revenue/export", "/api/payment-reports/daily",
                                "/api/payment-reports/daily/export", "/api/chi/tenants", "/api/chi/tenants/" + ID,
                                "/api/chi/tenants/" + ID + "/vehicles", "/api/chi/tenants/residents/room/" + ID,
                                "/api/invoice-history", "/api/monthly-statements", "/api/monthly-statements/export",
                                "/api/contracts/" + ID + "/deposit")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/api/payments", "/api/payments/qr", "/api/payments/match",
                                "/api/payments/" + ID + "/confirm", "/api/payments/" + ID + "/cancel", "/api/invoices/" + ID + "/send-app",
                                "/api/invoices/" + ID + "/send-email", "/api/invoices/" + ID + "/share-link", "/api/receipts",
                                "/api/invoices/" + ID + "/partial-payments", "/api/invoices/" + ID + "/overpayment/to-balance",
                                "/api/receipts/" + ID + "/send-confirmation", "/api/prepayments/deduct",
                                "/api/debts/recalculate", "/api/contracts/" + ID + "/deposit")
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER")
                        .requestMatchers(HttpMethod.PUT, "/api/partial-payments/" + ID).hasAnyRole("LANDLORD", "MANAGER", "CASHIER")
                        .requestMatchers(HttpMethod.PATCH, "/api/invoices/" + ID + "/cancel").hasAnyRole("LANDLORD", "MANAGER", "CASHIER")
                        .requestMatchers(HttpMethod.DELETE, "/api/invoices/" + ID + "/share-link", "/api/prepayments/deductions/" + ID)
                        .hasAnyRole("LANDLORD", "MANAGER", "CASHIER")
                        .anyRequest().hasAnyRole("LANDLORD", "MANAGER");
                } else {
                    auth.anyRequest().permitAll(); // che do dev: mo, khong can token
                }
            })
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    // Nguoi dung go thang URL API tren trinh duyet (Accept co text/html): dua ve trang dang nhap
                    String accept = req.getHeader("Accept");
                    if ("GET".equals(req.getMethod()) && accept != null && accept.contains("text/html")) {
                        res.sendRedirect(req.getContextPath() + "/ui/index.html");
                        return;
                    }
                    writeError(res, mapper, HttpStatus.UNAUTHORIZED, "Chua dang nhap hoac token het han");
                })
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, mapper, HttpStatus.FORBIDDEN, "Khong co quyen thuc hien")))
            .addFilterBefore(new JwtAuthFilter(jwtService, statusChecker), UsernamePasswordAuthenticationFilter.class);
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
