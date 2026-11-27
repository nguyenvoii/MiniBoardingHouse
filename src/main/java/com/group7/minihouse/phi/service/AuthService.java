package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.JwtService;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantReadRepository tenantReadRepository;
    private final JdbcTemplate jdbc;
    // BCrypt cost 10, the same work factor as the configured password encoder.
    private static final String TIMING_HASH = "$2a$10$R8DntFtyKRRUPZzpbXjyGu9V65vRxnO.RZDhb1LaIX9I47vQlxrQu";
    private static final String DEMO_PASSWORD_HASH = "$2a$10$Pc6uubpMhGt6uuP/TqMCyulqTZc2GQVvfgNqWPYx7ciLqNtJzs3uy";

    @Value("${app.auth.allow-demo-accounts:false}")
    private boolean allowDemoAccounts;

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                       ActivityLogService activityLogService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TenantReadRepository tenantReadRepository, JdbcTemplate jdbc) {
        this.tenantReadRepository = tenantReadRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.activityLogService = activityLogService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress) {
        String id = req.identifier().trim();
        AttemptKey key = new AttemptKey(ipAddress, id.toLowerCase(Locale.ROOT));
        checkNotLocked(key);
        // Cung 1 thong bao cho "khong co tai khoan" va "sai mat khau" de khong lo tai khoan nao ton tai
        boolean tooLong = req.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72;
        User user = tooLong ? null : userRepository.findByEmailOrPhoneNumber(id, id).orElse(null);
        boolean matches = passwordEncoder.matches(tooLong ? "" : req.password(),
                user == null ? TIMING_HASH : user.getPasswordHash());
        if (user == null || !matches || (!allowDemoAccounts && DEMO_PASSWORD_HASH.equals(user.getPasswordHash()))) {
            recordFailure(key);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Sai tai khoan hoac mat khau");
        }
        clearFailure(key);

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tai khoan khong hoat dong (bi khoa hoac ngung)");
        }

        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getUserId());
        Integer tenantId = roles.contains("TENANT") ? findTenantId(user) : null;
        AuthUser authUser = new AuthUser(user.getUserId(), user.getEmail(), user.getFullName(), roles, tenantId);

        activityLogService.log(user.getUserId(), "LOGIN", "Dang nhap thanh cong", ipAddress);

        return new LoginResponse(jwtService.generate(authUser, user.getPasswordHash()), "Bearer", jwtService.getExpirationSeconds(), authUser,
                user.isMustChangePassword());
    }

    @Transactional
    public void logout(JwtService.Session session, String ip) {
        jdbc.update("""
                INSERT INTO dbo.revoked_sessions (session_id, expires_at)
                SELECT ?, ? WHERE NOT EXISTS (
                    SELECT 1 FROM dbo.revoked_sessions WITH (UPDLOCK, HOLDLOCK) WHERE session_id = ?
                )
                """, session.id(), session.expiresAt(), session.id());
        jdbc.update("DELETE FROM dbo.revoked_sessions WHERE expires_at <= ?", Instant.now().getEpochSecond());
        activityLogService.log(session.user().userId(), "LOGOUT", "Dang xuat", ip);
    }

    static final int MAX_FAILURES = 5;
    static final long LOCK_MINUTES = 5;
    private static final long WINDOW_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(LOCK_MINUTES);
    private static final int MAX_TRACKED_PAIRS = 10_000;
    private static final Map<AttemptKey, Attempts> FAILURES = new LinkedHashMap<>();
    private record AttemptKey(String address, String identifier) {}
    private record Attempts(int count, long expiresAt) {}
    private static long nextCleanup = System.nanoTime();

    /** Dung cho test: xoa trang thai dem. */
    static synchronized void resetFailures() {
        FAILURES.clear();
    }

    private static synchronized void checkNotLocked(AttemptKey key) {
        long now = System.nanoTime();
        if (now - nextCleanup >= 0) {
            FAILURES.values().removeIf(attempts -> now - attempts.expiresAt() >= 0);
            nextCleanup = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        }
        Attempts attempts = FAILURES.get(key);
        if (attempts != null && now - attempts.expiresAt() < 0 && attempts.count() >= MAX_FAILURES) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dang nhap sai qua nhieu lan, vui long thu lai sau " + LOCK_MINUTES + " phut");
        }
    }

    private static synchronized void recordFailure(AttemptKey key) {
        long now = System.nanoTime();
        Attempts previous = FAILURES.get(key);
        if (previous == null && FAILURES.size() >= MAX_TRACKED_PAIRS) {
            FAILURES.remove(FAILURES.keySet().iterator().next());
        }
        if (previous == null || now - previous.expiresAt() >= 0) {
            FAILURES.put(key, new Attempts(1, now + WINDOW_NANOS));
        } else if (previous.count() < MAX_FAILURES) {
            int count = previous.count() + 1;
            FAILURES.put(key, new Attempts(count, count == MAX_FAILURES ? now + WINDOW_NANOS : previous.expiresAt()));
        }
    }

    private static synchronized void clearFailure(AttemptKey key) {
        FAILURES.remove(key);
    }

    /** Tai khoan nguoi thue noi voi ho so khach thue qua so dien thoai (xem docs/thiet-ke/tai-khoan-nguoi-thue.md). */
    private Integer findTenantId(User user) {
        String phone = user.getPhoneNumber() == null ? null : user.getPhoneNumber().trim();
        if (phone == null || phone.isEmpty()) {
            throw noTenantProfile();
        }
        Tenant tenant = tenantReadRepository.findByPhone(phone).orElseThrow(AuthService::noTenantProfile);
        // Tai khoan phai duoc tao SAU ho so khach thue. Neu cu hon thi SDT da bi tai su dung cho nguoi khac:
        // khong cho tai khoan cu nhan du lieu cua nguoi moi.
        if (user.getCreatedAt() != null && tenant.getCreatedAt() != null
                && user.getCreatedAt().isBefore(tenant.getCreatedAt())) {
            throw noTenantProfile();
        }
        return tenant.getTenantId();
    }

    private static BusinessException noTenantProfile() {
        return new BusinessException(HttpStatus.CONFLICT,
                "Tai khoan chua lien ket ho so khach thue, lien he chu tro");
    }
}
