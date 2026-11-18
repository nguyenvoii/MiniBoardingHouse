package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.ProfileResponse;
import com.group7.minihouse.phi.dto.UpdateProfileRequest;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC5 doi mat khau, UC6 ho so ca nhan, UC7 anh dai dien: moi nguoi dung (ke ca nguoi thue) thao tac tren chinh tai khoan cua minh. */
@Service
public class AccountService {

    public static final String AVATAR_FOLDER = "avatars";
    static final String AVATAR_URL_PREFIX = "/api/auth/avatars/";
    static final long AVATAR_MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final FileStorage fileStorage;

    public AccountService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                          PasswordEncoder passwordEncoder, ActivityLogService activityLogService,
                          FileStorage fileStorage) {
        this.fileStorage = fileStorage;
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
        user.setMustChangePassword(false);
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
        // Khong gui phoneNumber = giu nguyen; gui chuoi rong/khoang trang = xoa SDT
        String oldPhone = blankToNull(user.getPhoneNumber());
        String newPhone = req.phoneNumber() == null ? oldPhone : blankToNull(req.phoneNumber());
        if (newPhone != null && !PHONE.matcher(newPhone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "So dien thoai chi gom 9-15 chu so (co the bat dau bang +), khong co dau cach");
        }

        if (!Objects.equals(newPhone, oldPhone)) {
            // Tai khoan nguoi thue noi voi ho so khach thue bang SDT: doi SDT se lam dut lien ket
            if (roles.contains("TENANT")) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Nguoi thue khong tu doi duoc so dien thoai, vui long lien he chu tro");
            }
            if (newPhone == null && blankToNull(user.getEmail()) == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Khong the xoa so dien thoai vi tai khoan chua co email, se khong con cach dang nhap");
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

    /** UC7: dat anh dai dien (PNG/JPG/WebP, toi da 2 MB). Anh cu bi xoa. */
    @Transactional
    public ProfileResponse updateAvatar(Integer userId, byte[] image, String ip) {
        User user = load(userId);
        String name = fileStorage.saveImage(AVATAR_FOLDER, image, AVATAR_MAX_BYTES);
        String old = user.getAvatarUrl();
        user.setAvatarUrl(AVATAR_URL_PREFIX + name);
        userRepository.save(user);
        deleteStoredAvatar(old);
        activityLogService.log(userId, "UPDATE_AVATAR", "Cap nhat anh dai dien", ip);
        return toProfile(user, userRoleRepository.findRoleCodesByUserId(userId));
    }

    /** UC7: go anh dai dien. */
    @Transactional
    public ProfileResponse removeAvatar(Integer userId, String ip) {
        User user = load(userId);
        String old = user.getAvatarUrl();
        user.setAvatarUrl(null);
        userRepository.save(user);
        deleteStoredAvatar(old);
        activityLogService.log(userId, "REMOVE_AVATAR", "Go anh dai dien", ip);
        return toProfile(user, userRoleRepository.findRoleCodesByUserId(userId));
    }

    private void deleteStoredAvatar(String url) {
        if (url != null && url.startsWith(AVATAR_URL_PREFIX)) {
            fileStorage.delete(AVATAR_FOLDER, url.substring(AVATAR_URL_PREFIX.length()));
        }
    }

    private User load(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("Nguoi dung"));
    }

    static boolean isStrong(String p) {
        return p != null && p.length() >= 8 && p.chars().anyMatch(Character::isLetter)
                && p.chars().anyMatch(Character::isDigit);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ProfileResponse toProfile(User u, List<String> roles) {
        return new ProfileResponse(u.getUserId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getAvatarUrl(),
                roles);
    }
}
