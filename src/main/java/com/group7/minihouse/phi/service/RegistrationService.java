package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.RegisterRequest;
import com.group7.minihouse.phi.repository.RoleRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Role;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.entity.UserRoleId;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.notification.NotificationSender;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC1 dang ky chu tro, UC2 xac thuc email bang OTP. Dang ky tao tai khoan LANDLORD o trang thai INACTIVE,
 * xac thuc dung ma moi chuyen sang ACTIVE. Ca 3 API la cong khai (chua dang nhap).
 * Tai khoan "cho xac thuc" = INACTIVE + vai tro LANDLORD (nhan vien bi ngung hoat dong khong tu kich hoat duoc).
 */
@Service
public class RegistrationService {

    private static final String TYPE = "EMAIL";
    private static final String BAD_CODE = "Ma xac thuc khong dung hoac da het han";
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OtpService otpService;
    private final NotificationSender sender;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public RegistrationService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                               RoleRepository roleRepository, OtpService otpService, NotificationSender sender,
                               PasswordEncoder passwordEncoder, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.otpService = otpService;
        this.sender = sender;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void register(RegisterRequest req, String ip) {
        String name = req.fullName() == null ? "" : req.fullName().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ho ten khong duoc de trong va toi da 100 ky tu");
        }
        String email = blankToNull(req.email());
        if (email != null) {
            email = email.toLowerCase();
        }
        if (email == null || email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email khong hop le");
        }
        String phone = blankToNull(req.phoneNumber());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "So dien thoai chi gom 9-15 chu so (co the bat dau bang +), khong co dau cach");
        }
        if (!AccountService.isStrong(req.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mat khau phai co it nhat 8 ky tu, gom ca chu va so");
        }

        User existing = userRepository.findByEmailOrPhoneNumber(email, email).orElse(null);
        User user;
        if (existing == null) {
            if (phone != null && userRepository.existsByPhoneNumber(phone)) {
                throw conflict("So dien thoai da duoc tai khoan khac su dung");
            }
            user = new User();
            user.setEmail(email);
            user.setStatus("INACTIVE");
        } else if (isPending(existing)) {
            // Dang ky lai tren tai khoan chua xac thuc: ghi de, de nguoi chiem email truoc khong giu duoc mat khau
            if (phone != null && userRepository.existsByPhoneNumberAndUserIdNot(phone, existing.getUserId())) {
                throw conflict("So dien thoai da duoc tai khoan khac su dung");
            }
            user = existing;
        } else {
            throw conflict("Email da duoc tai khoan khac su dung");
        }
        user.setFullName(name);
        user.setPhoneNumber(phone);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        boolean isNew = user.getUserId() == null;
        User saved = userRepository.save(user);
        if (isNew) {
            userRoleRepository.save(newUserRole(saved, landlordRole()));
        }
        sendCode(saved);
    }

    @Transactional
    public void verifyEmail(String email, String code, String ip) {
        User user = userRepository.findByEmailOrPhoneNumber(email.trim(), email.trim())
                .filter(this::isPending)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        if (!otpService.verify(user, TYPE, code)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        user.setStatus("ACTIVE");
        userRepository.save(user);
        activityLogService.log(user.getUserId(), "VERIFY_EMAIL", "Xac thuc email, kich hoat tai khoan", ip);
    }

    /** UC2: gui OTP xac thuc so dien thoai. Ban demo dung NotificationSender ghi log. */
    @Transactional
    public void requestPhoneVerification(Integer actorId, String phone) {
        User user = userRepository.findById(actorId)
                .filter(account -> phone != null && phone.trim().equals(account.getPhoneNumber()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        String code = otpService.issue(user, "PHONE");
        sender.send(user.getPhoneNumber(), "Ma xac thuc so dien thoai MiniBoardingHouse",
                "Ma xac thuc cua ban la " + code + ". Ma co hieu luc 10 phut, khong chia se ma nay cho ai.");
    }

    /** UC2: xac thuc OTP so dien thoai. */
    @Transactional
    public void verifyPhone(Integer actorId, String phone, String code, String ip) {
        User user = userRepository.findById(actorId)
                .filter(account -> phone != null && phone.trim().equals(account.getPhoneNumber()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        if (!otpService.verify(user, "PHONE", code)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        activityLogService.log(user.getUserId(), "VERIFY_PHONE", "Xac thuc so dien thoai", ip);
    }

    /** Gui lai ma xac thuc. Im lang neu khong co tai khoan cho xac thuc hoac vua gui (khong lo tai khoan co ton tai). */
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmailOrPhoneNumber(email.trim(), email.trim())
                .filter(this::isPending).orElse(null);
        if (user != null) {
            sendCode(user);
        }
    }

    private boolean isPending(User u) {
        return "INACTIVE".equals(u.getStatus())
                && userRoleRepository.findRoleCodesByUserId(u.getUserId()).contains("LANDLORD");
    }

    private void sendCode(User user) {
        String code;
        try {
            code = otpService.issue(user, TYPE);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                return;
            }
            throw e;
        }
        sender.send(user.getEmail(), "Mã xác thực email MiniBoardingHouse",
                "Mã xác thực của bạn là " + code + ". Mã có hiệu lực 10 phút, không chia sẻ mã này cho ai.");
    }

    private Role landlordRole() {
        return roleRepository.findByRoleCode("LANDLORD").orElseThrow(() ->
                new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Chua nap role LANDLORD, chay db/seed.sql"));
    }

    private static UserRole newUserRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setId(new UserRoleId(user.getUserId(), role.getRoleId()));
        ur.setUser(user);
        ur.setRole(role);
        return ur;
    }

    private static BusinessException conflict(String msg) {
        return new BusinessException(HttpStatus.CONFLICT, msg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
