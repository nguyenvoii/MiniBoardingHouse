package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
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
    private final UserActivityLogRepository logRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantReadRepository tenantReadRepository;

    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                       UserActivityLogRepository logRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TenantReadRepository tenantReadRepository) {
        this.tenantReadRepository = tenantReadRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.logRepository = logRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress) {
        String id = req.identifier().trim();
        // Cung 1 thong bao cho "khong co tai khoan" va "sai mat khau" de khong lo tai khoan nao ton tai
        User user = userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Sai tai khoan hoac mat khau"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tai khoan khong hoat dong (bi khoa hoac ngung)");
        }

        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getUserId());
        Integer tenantId = roles.contains("TENANT") ? findTenantId(user) : null;
        AuthUser authUser = new AuthUser(user.getUserId(), user.getEmail(), user.getFullName(), roles, tenantId);

        UserActivityLog log = new UserActivityLog();
        log.setUser(user);
        log.setActionType("LOGIN");
        log.setDescription("Dang nhap thanh cong");
        log.setIpAddress(ipAddress);
        logRepository.save(log);

        return new LoginResponse(jwtService.generate(authUser), "Bearer", jwtService.getExpirationSeconds(), authUser);
    }

    /** Tai khoan nguoi thue noi voi ho so khach thue qua so dien thoai (xem docs/superpowers/specs/...tenant-account). */
    private Integer findTenantId(User user) {
        String phone = user.getPhoneNumber() == null ? null : user.getPhoneNumber().trim();
        if (phone == null || phone.isEmpty()) {
            throw noTenantProfile();
        }
        return tenantReadRepository.findByPhone(phone).map(Tenant::getTenantId)
                .orElseThrow(AuthService::noTenantProfile);
    }

    private static BusinessException noTenantProfile() {
        return new BusinessException(HttpStatus.CONFLICT,
                "Tai khoan chua lien ket ho so khach thue, lien he chu tro");
    }
}
