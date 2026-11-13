package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.ProfileResponse;
import com.group7.minihouse.phi.dto.UpdateProfileRequest;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC5 doi mat khau, UC6 ho so ca nhan: moi nguoi dung (ke ca nguoi thue) thao tac tren chinh tai khoan cua minh. */
@Service
public class AccountService {

    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public AccountService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                          PasswordEncoder passwordEncoder, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void changePassword(Integer userId, String currentPassword, String newPassword, String ip) {
        User user = load(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mat khau hien tai khong dung");
        }
        if (!isStrong(newPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mat khau moi phai co it nhat 8 ky tu, gom ca chu va so");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mat khau moi phai khac mat khau hien tai");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        activityLogService.log(userId, "CHANGE_PASSWORD", "Doi mat khau", ip);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Integer userId) {
        User user = load(userId);
        return toProfile(user, userRoleRepository.findRoleCodesByUserId(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Integer userId, UpdateProfileRequest req, String ip) {
        User user = load(userId);
        List<String> roles = userRoleRepository.findRoleCodesByUserId(userId);

        String name = req.fullName() == null ? "" : req.fullName().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ho ten khong duoc de trong va toi da 100 ky tu");
        }
        String newPhone = blankToNull(req.phoneNumber());
        if (newPhone != null && !PHONE.matcher(newPhone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "So dien thoai chi gom 9-15 chu so (co the bat dau bang +), khong co dau cach");
        }

        if (!Objects.equals(newPhone, blankToNull(user.getPhoneNumber()))) {
            // Tai khoan nguoi thue noi voi ho so khach thue bang SDT: doi SDT se lam dut lien ket
            if (roles.contains("TENANT")) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Nguoi thue khong tu doi duoc so dien thoai, vui long lien he chu tro");
            }
            if (newPhone != null) {
                userRepository.findByPhoneNumber(newPhone).filter(o -> !o.getUserId().equals(userId))
                        .ifPresent(o -> {
                            throw new BusinessException(HttpStatus.CONFLICT, "So dien thoai da duoc tai khoan khac su dung");
                        });
            }
        }

        user.setFullName(name);
        user.setPhoneNumber(newPhone);
        userRepository.save(user);
        activityLogService.log(userId, "UPDATE_PROFILE", "Cap nhat ho so ca nhan", ip);
        return toProfile(user, roles);
    }

    private User load(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("Nguoi dung"));
    }

    private static boolean isStrong(String p) {
        return p != null && p.length() >= 8 && p.chars().anyMatch(Character::isLetter)
                && p.chars().anyMatch(Character::isDigit);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ProfileResponse toProfile(User u, List<String> roles) {
        return new ProfileResponse(u.getUserId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), roles);
    }
}
