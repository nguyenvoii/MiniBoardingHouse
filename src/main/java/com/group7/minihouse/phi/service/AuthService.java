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

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                       ActivityLogService activityLogService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TenantReadRepository tenantReadRepository) {
        this.tenantReadRepository = tenantReadRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.activityLogService = activityLogService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress) {
        String id = req.identifier().trim();
        String key = id.toLowerCase();
        checkNotLocked(key);
        // Cung 1 thong bao cho "khong co tai khoan" va "sai mat khau" de khong lo tai khoan nao ton tai
        boolean tooLong = req.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72;
        User user = tooLong ? null : userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            recordFailure(key);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Sai tai khoan hoac mat khau");
        }
        FAILURES.remove(key);

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tai khoan khong hoat dong (bi khoa hoac ngung)");
        }

        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getUserId());
        Integer tenantId = roles.contains("TENANT") ? findTenantId(user) : null;
        AuthUser authUser = new AuthUser(user.getUserId(), user.getEmail(), user.getFullName(), roles, tenantId);

        activityLogService.log(user.getUserId(), "LOGIN", "Dang nhap thanh cong", ipAddress);

        return new LoginResponse(jwtService.generate(authUser), "Bearer", jwtService.getExpirationSeconds(), authUser,
                user.isMustChangePassword());
    }

    // Chong do mat khau don gian (do an): dem lan sai theo tai khoan trong bo nho, sai MAX_FAILURES lan thi khoa tam
    // LOCK_MINUTES phut. Khoi dong lai may chu thi xoa; khong can bang rieng.
    static final int MAX_FAILURES = 5;
    static final long LOCK_MINUTES = 5;
    private static final java.util.Map<String, long[]> FAILURES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Dung cho test: xoa trang thai dem. */
    static void resetFailures() {
        FAILURES.clear();
    }

    private static void checkNotLocked(String key) {
        long[] f = FAILURES.get(key);
        if (f != null && f[1] > System.currentTimeMillis()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dang nhap sai qua nhieu lan, vui long thu lai sau " + LOCK_MINUTES + " phut");
        }
    }

    private static void recordFailure(String key) {
        FAILURES.compute(key, (k, f) -> {
            long now = System.currentTimeMillis();
            long[] cur = f == null || (f[1] != 0 && f[1] <= now) ? new long[] {0, 0} : f; // het han khoa thi dem lai
            cur[0]++;
            if (cur[0] >= MAX_FAILURES) {
                cur[1] = now + LOCK_MINUTES * 60_000;
            }
            return cur;
        });
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
