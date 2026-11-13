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
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC10: cap tai khoan dang nhap cho nguoi thue, noi voi ho so khach thue qua so dien thoai. */
@Service
public class TenantAccountService {

    /** Bo cac ky tu de nham (0 O 1 l I) vi mat khau tam duoc doc/gui cho nguoi thue. */
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();
    private final TenantReadRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantAccountService(TenantReadRepository tenantRepository, UserRepository userRepository,
                                UserRoleRepository userRoleRepository, RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantAccountResponse createAccount(Integer tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khach thue"));

        String phone = tenant.getPhoneNumber().trim();
        if (phone.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ho so khach thue thieu so dien thoai");
        }
        String email = tenant.getEmail() == null || tenant.getEmail().isBlank() ? null : tenant.getEmail().trim();

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khach thue nay da co tai khoan dang nhap");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email cua khach thue da duoc tai khoan khac su dung");
        }
        Role role = roleRepository.findByRoleCode("TENANT").orElseThrow(() ->
                new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Chua nap role TENANT, chay db/seed.sql"));

        String tempPassword = generatePassword();
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

        return new TenantAccountResponse(saved.getUserId(), phone, tempPassword);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
