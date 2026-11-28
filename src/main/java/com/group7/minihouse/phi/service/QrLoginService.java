package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ma QR dang nhap cho nguoi thue vua duoc cap tai khoan: quet la vao web, khong phai go SDT va mat khau tam.
 * Ma chi dung duoc khi tai khoan CON dung mat khau tam (must_change_password) va mat khau tam khong bi cap lai;
 * khach dat mat khau rieng la ma het hieu luc. Het han sau 24 gio.
 */
@Service
public class QrLoginService {

    static final long LINK_MINUTES = 24 * 60;

    public record Link(String token, long expiresInSeconds) {}

    private final TenantReadRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActivityLogService activityLogService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public QrLoginService(TenantReadRepository tenantRepository, UserRepository userRepository,
                          UserRoleRepository userRoleRepository, ActivityLogService activityLogService,
                          JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.activityLogService = activityLogService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Nhan vien tao ma cho tai khoan nguoi thue dang dung mat khau tam. */
    @Transactional(readOnly = true)
    public Link issue(Integer tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        String phone = tenant.getPhoneNumber() == null ? "" : tenant.getPhoneNumber().trim();
        User account = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> BusinessException.notFound("Tài khoản của khách thuê"));
        boolean orphan = account.getCreatedAt() != null && tenant.getCreatedAt() != null
                && account.getCreatedAt().isBefore(tenant.getCreatedAt());
        if (orphan || !userRoleRepository.findRoleCodesByUserId(account.getUserId()).contains("TENANT")) {
            throw BusinessException.notFound("Tài khoản của khách thuê");
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Tài khoản đang bị khóa hoặc ngừng");
        }
        if (!account.isMustChangePassword()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Khách đã tự đặt mật khẩu, không tạo mã QR đăng nhập. Cần thì cấp lại mật khẩu tạm trước");
        }
        String token = jwtService.generateQrLogin(account.getUserId(), tenantId, fingerprint(account), LINK_MINUTES);
        return new Link(token, LINK_MINUTES * 60);
    }

    /** Khach quet ma: doi token lay phien dang nhap nhu dang nhap thuong (van phai doi mat khau tam). */
    @Transactional
    public LoginResponse login(String token, String ip) {
        JwtService.QrClaims claims = token == null ? null : jwtService.parseQrLogin(token);
        User user = claims == null ? null : userRepository.findById(claims.userId()).orElse(null);
        List<String> roles = user == null ? List.of() : userRoleRepository.findRoleCodesByUserId(user.getUserId());
        boolean valid = user != null && "ACTIVE".equals(user.getStatus()) && user.isMustChangePassword()
                && roles.contains("TENANT") && claims.tenantId() != null
                && MessageDigest.isEqual(fingerprint(user).getBytes(StandardCharsets.UTF_8),
                        String.valueOf(claims.fingerprint()).getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Mã QR hết hạn hoặc đã dùng xong, nhờ nhân viên cấp mã mới");
        }
        AuthUser authUser = new AuthUser(user.getUserId(), user.getEmail(), user.getFullName(), roles, claims.tenantId());
        activityLogService.log(user.getUserId(), "LOGIN", "Dang nhap bang ma QR", ip);
        return new LoginResponse(jwtService.generate(authUser, true), "Bearer", jwtService.getExpirationSeconds(), authUser, true);
    }

    /**
     * Khach vao bang ma QR thi dat mat khau moi ngay, khong can biet mat khau tam. Chi dung duoc khi tai khoan CON
     * dung mat khau tam; dat xong thi ma QR cu mat hieu luc (dau van tay mat khau da doi).
     */
    @Transactional
    public void setFirstPassword(Integer userId, String newPassword, String ip) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("Tài khoản"));
        if (!user.isMustChangePassword()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Tài khoản đã có mật khẩu riêng, hãy dùng chức năng đổi mật khẩu");
        }
        if (!AccountService.isStrong(newPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải có ít nhất 8 ký tự, gồm cả chữ và số");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        activityLogService.log(userId, "CHANGE_PASSWORD", "Dat mat khau moi lan dau (vao bang ma QR)", ip);
    }

    /** Dau van tay cua mat khau hien tai: cap lai mat khau tam hoac doi mat khau thi doi theo, ma QR cu mat hieu luc. */
    private static String fingerprint(User user) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(user.getPasswordHash().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
