package com.group7.minihouse.phi.service;

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
                .orElseThrow(() -> BusinessException.notFound("Khach thue"));

        String phone = tenant.getPhoneNumber().trim();
        if (phone.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ho so khach thue thieu so dien thoai");
        }
        String email = tenant.getEmail() == null || tenant.getEmail().isBlank() ? null : tenant.getEmail().trim();

        userRepository.findByPhoneNumber(phone).ifPresent(existing -> resolveExistingAccount(existing, tenant));
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email cua khach thue da duoc tai khoan khac su dung");
        }
        Role role = roleRepository.findByRoleCode("TENANT").orElseThrow(() ->
                new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Chua nap role TENANT, chay db/seed.sql"));

        String tempPassword = PasswordGenerator.generate();
        User user = new User();
        user.setPhoneNumber(phone);
        user.setEmail(email);
        user.setFullName(tenant.getFullName());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
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

    /**
     * Khoa (LOCKED) hoac mo khoa (ACTIVE) tai khoan dang nhap cua khach thue, vi du khi khach tra phong.
     * Co hieu luc ngay voi token da cap (xem UserStatusChecker). Chi tac dong len dung tai khoan cua khach thue nay:
     * khong co tai khoan, hoac tai khoan tao truoc ho so (SDT bi tai su dung) -> 404; SDT thuoc nhan vien -> 409.
     */
    @Transactional
    public void setAccountStatus(Integer tenantId, String status, Integer actorUserId, String ip) {
        if (!"ACTIVE".equals(status) && !"LOCKED".equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Trang thai chi nhan ACTIVE (mo khoa) hoac LOCKED (khoa)");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khach thue"));
        String phone = tenant.getPhoneNumber() == null ? "" : tenant.getPhoneNumber().trim();
        User account = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> BusinessException.notFound("Tai khoan cua khach thue"));
        if (!userRoleRepository.findRoleCodesByUserId(account.getUserId()).contains("TENANT")) {
            throw new BusinessException(HttpStatus.CONFLICT, "So dien thoai nay dang duoc tai khoan nhan vien su dung");
        }
        if (account.getCreatedAt() != null && tenant.getCreatedAt() != null
                && account.getCreatedAt().isBefore(tenant.getCreatedAt())) {
            throw BusinessException.notFound("Tai khoan cua khach thue");
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
            throw new BusinessException(HttpStatus.CONFLICT, "So dien thoai nay da duoc tai khoan nhan vien su dung");
        }
        boolean orphan = existing.getCreatedAt() != null && tenant.getCreatedAt() != null
                && existing.getCreatedAt().isBefore(tenant.getCreatedAt());
        if (!orphan) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khach thue nay da co tai khoan dang nhap");
        }
        existing.setStatus("INACTIVE");
        existing.setPhoneNumber(null);
        existing.setEmail(null);
        userRepository.saveAndFlush(existing);
    }
}
