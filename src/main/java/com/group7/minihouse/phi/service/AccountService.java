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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        if (!isStrong(newPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới phải có ít nhất 8 ký tự, gồm cả chữ và số");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Họ tên không được để trống và tối đa 100 ký tự");
        }
        // Khong gui phoneNumber = giu nguyen; gui chuoi rong/khoang trang = xoa SDT
        String oldPhone = blankToNull(user.getPhoneNumber());
        String newPhone = req.phoneNumber() == null ? oldPhone : blankToNull(req.phoneNumber());
        if (newPhone != null && !PHONE.matcher(newPhone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Số điện thoại chỉ gồm 9-15 chữ số (có thể bắt đầu bằng +), không có dấu cách");
        }

        if (!Objects.equals(newPhone, oldPhone)) {
            // Tai khoan nguoi thue noi voi ho so khach thue bang SDT: doi SDT se lam dut lien ket
            if (roles.contains("TENANT")) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Người thuê không tự đổi được số điện thoại, vui lòng liên hệ chủ trọ");
            }
            if (newPhone == null && blankToNull(user.getEmail()) == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Không thể xóa số điện thoại vì tài khoản chưa có email, sẽ không còn cách đăng nhập");
            }
            if (newPhone != null) {
                userRepository.findByPhoneNumber(newPhone).filter(o -> !o.getUserId().equals(userId))
                        .ifPresent(o -> {
                            throw new BusinessException(HttpStatus.CONFLICT, "Số điện thoại đã được tài khoản khác sử dụng");
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
        return userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("Người dùng"));
    }

    static boolean isStrong(String p) {
        // BCrypt chi dung 72 byte dau: mat khau dai hon se bi cat im lang (phan sau khong co tac dung) nen chan tu dau
        return p != null && p.length() >= 8 && p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && p.chars().anyMatch(Character::isLetter) && p.chars().anyMatch(Character::isDigit);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ProfileResponse toProfile(User u, List<String> roles) {
        return new ProfileResponse(u.getUserId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getAvatarUrl(),
                roles);
    }
}
