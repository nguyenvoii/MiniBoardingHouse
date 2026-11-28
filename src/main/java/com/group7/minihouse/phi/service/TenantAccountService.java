package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.TenantAccountInfo;
import com.group7.minihouse.phi.dto.TenantAccountResponse;
import com.group7.minihouse.phi.repository.RoleRepository;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Role;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.entity.UserRoleId;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.util.PasswordGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC10: cap tai khoan dang nhap cho nguoi thue, noi voi ho so khach thue qua so dien thoai. */
@Service
public class TenantAccountService {

    private final TenantReadRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public TenantAccountService(TenantReadRepository tenantRepository, UserRepository userRepository,
                                UserRoleRepository userRoleRepository, RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder, ActivityLogService activityLogService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    /** actorUserId la nguoi cap tai khoan (null khi che do dev khong co token: khi do khong ghi nhat ky). */
    @Transactional
    public TenantAccountResponse createAccount(Integer tenantId, Integer actorUserId, String ip) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));

        String phone = tenant.getPhoneNumber().trim();
        if (phone.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Hồ sơ khách thuê thiếu số điện thoại");
        }
        String email = tenant.getEmail() == null || tenant.getEmail().isBlank() ? null : tenant.getEmail().trim();

        userRepository.findByPhoneNumber(phone).ifPresent(existing -> resolveExistingAccount(existing, tenant));
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email của khách thuê đã được tài khoản khác sử dụng");
        }
        Role role = roleRepository.findByRoleCode("TENANT").orElseThrow(() ->
                new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Chưa nạp role TENANT, chạy db/seed.sql"));

        String tempPassword = PasswordGenerator.generate();
        User user = new User();
        user.setPhoneNumber(phone);
        user.setEmail(email);
        user.setFullName(tenant.getFullName());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(saved.getUserId(), role.getRoleId()));
        userRole.setUser(saved);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        if (actorUserId != null) {
            activityLogService.log(actorUserId, "CREATE_TENANT_ACCOUNT",
                    "Cap tai khoan nguoi thue cho khach thue " + tenantId + " (user " + saved.getUserId() + ")", ip);
        }
        return new TenantAccountResponse(saved.getUserId(), phone, tempPassword);
    }

    /** Cấp lại mật khẩu tạm cho người thuê quên mật khẩu (nhân viên đọc cho khách). Mật khẩu cũ mất hiệu lực ngay. */
    @Transactional
    public TenantAccountResponse resetPassword(Integer tenantId, Integer actorUserId, String ip) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        String phone = tenant.getPhoneNumber() == null ? "" : tenant.getPhoneNumber().trim();
        User account = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> BusinessException.notFound("Tài khoản của khách thuê"));
        if (!userRoleRepository.findRoleCodesByUserId(account.getUserId()).contains("TENANT")) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại này đang được tài khoản nhân viên sử dụng");
        }
        String tempPassword = PasswordGenerator.generate();
        account.setPasswordHash(passwordEncoder.encode(tempPassword));
        account.setMustChangePassword(true);
        userRepository.save(account);
        if (actorUserId != null) {
            activityLogService.log(actorUserId, "RESET_TENANT_PASSWORD",
                    "Cap lai mat khau tam cho nguoi thue cua khach thue " + tenantId + " (user " + account.getUserId() + ")", ip);
        }
        return new TenantAccountResponse(account.getUserId(), phone, tempPassword);
    }

    /** Xem khach thue da co tai khoan dang nhap chua (cung quy tac nhan dien tai khoan nhu khi khoa/mo khoa). */
    @Transactional(readOnly = true)
    public TenantAccountInfo accountInfo(Integer tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        String phone = tenant.getPhoneNumber() == null ? "" : tenant.getPhoneNumber().trim();
        User account = phone.isEmpty() ? null : userRepository.findByPhoneNumber(phone).orElse(null);
        boolean mine = account != null
                && userRoleRepository.findRoleCodesByUserId(account.getUserId()).contains("TENANT")
                && !(account.getCreatedAt() != null && tenant.getCreatedAt() != null
                        && account.getCreatedAt().isBefore(tenant.getCreatedAt()));
        return mine ? new TenantAccountInfo(true, account.getUserId(), account.getPhoneNumber(), account.getStatus())
                : new TenantAccountInfo(false, null, phone, null);
    }

    /**
     * Khoa (LOCKED) hoac mo khoa (ACTIVE) tai khoan dang nhap cua khach thue, vi du khi khach tra phong.
     * Co hieu luc ngay voi token da cap (xem UserStatusChecker). Chi tac dong len dung tai khoan cua khach thue nay:
     * khong co tai khoan, hoac tai khoan tao truoc ho so (SDT bi tai su dung) -> 404; SDT thuoc nhan vien -> 409.
     */
    @Transactional
    public void setAccountStatus(Integer tenantId, String status, Integer actorUserId, String ip) {
        if (!"ACTIVE".equals(status) && !"LOCKED".equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Trạng thái chỉ nhận ACTIVE (mở khóa) hoặc LOCKED (khóa)");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        String phone = tenant.getPhoneNumber() == null ? "" : tenant.getPhoneNumber().trim();
        User account = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> BusinessException.notFound("Tài khoản của khách thuê"));
        if (!userRoleRepository.findRoleCodesByUserId(account.getUserId()).contains("TENANT")) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại này đang được tài khoản nhân viên sử dụng");
        }
        if (account.getCreatedAt() != null && tenant.getCreatedAt() != null
                && account.getCreatedAt().isBefore(tenant.getCreatedAt())) {
            throw BusinessException.notFound("Tài khoản của khách thuê");
        }
        account.setStatus(status);
        userRepository.save(account);

        if (actorUserId != null) {
            boolean lock = "LOCKED".equals(status);
            activityLogService.log(actorUserId, lock ? "LOCK_USER" : "UNLOCK_USER",
                    (lock ? "Khoa" : "Mo khoa") + " tai khoan nguoi thue cua khach thue " + tenantId
                            + " (user " + account.getUserId() + ")", ip);
        }
    }

    /**
     * Da co user dung SDT nay. Nhan vien -> bao dung. Tai khoan nguoi thue TAO SAU ho so nay -> da cap roi (409).
     * Tai khoan nguoi thue tao TRUOC ho so (SDT bi tai su dung cho khach moi) -> vo hieu hoa va giai phong SDT/email;
     * phai flush ngay vi Hibernate chay INSERT truoc UPDATE, neu khong se dung unique index UX_users_phone.
     */
    private void resolveExistingAccount(User existing, Tenant tenant) {
        if (!userRoleRepository.findRoleCodesByUserId(existing.getUserId()).contains("TENANT")) {
            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại này đã được tài khoản nhân viên sử dụng");
        }
        boolean orphan = existing.getCreatedAt() != null && tenant.getCreatedAt() != null
                && existing.getCreatedAt().isBefore(tenant.getCreatedAt());
        if (!orphan) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khách thuê này đã có tài khoản đăng nhập");
        }
        existing.setStatus("INACTIVE");
        existing.setPhoneNumber(null);
        existing.setEmail(null);
        userRepository.saveAndFlush(existing);
    }
}
