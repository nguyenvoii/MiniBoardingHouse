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
import com.group7.minihouse.shared.notification.OtpMail;
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
    private static final String BAD_CODE = "Mã xác thực không đúng hoặc đã hết hạn";
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** Co khi app.otp.provider=eotp: ma xac thuc SO DIEN THOAI gui qua Zalo (eOTP) thay vi ma noi bo. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    ExternalOtpService external;

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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Họ tên không được để trống và tối đa 100 ký tự");
        }
        String email = blankToNull(req.email());
        if (email != null) {
            email = email.toLowerCase();
        }
        if (email == null || email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        String phone = blankToNull(req.phoneNumber());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Số điện thoại chỉ gồm 9-15 chữ số (có thể bắt đầu bằng +), không có dấu cách");
        }
        if (!AccountService.isStrong(req.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số");
        }

        User existing = userRepository.findByEmailOrPhoneNumber(email, email).orElse(null);
        User user;
        if (existing == null) {
            if (phone != null && userRepository.existsByPhoneNumber(phone)) {
                throw conflict("Số điện thoại đã được tài khoản khác sử dụng");
            }
            user = new User();
            user.setEmail(email);
            user.setStatus("INACTIVE");
        } else if (isPending(existing)) {
            // Dang ky lai tren tai khoan chua xac thuc: ghi de, de nguoi chiem email truoc khong giu duoc mat khau
            if (phone != null && userRepository.existsByPhoneNumberAndUserIdNot(phone, existing.getUserId())) {
                throw conflict("Số điện thoại đã được tài khoản khác sử dụng");
            }
            user = existing;
        } else {
            throw conflict("Email đã được tài khoản khác sử dụng");
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
        if (external != null) {
            external.send(user, "PHONE", user.getPhoneNumber());
            return;
        }
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
        boolean ok = external != null && external.handles(user, "PHONE")
                ? external.verify(user, "PHONE", code) : otpService.verify(user, "PHONE", code);
        if (!ok) {
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
        OtpMail mail = OtpMail.emailVerification(code);
        sender.sendHtml(user.getEmail(), mail.subject(), mail.text(), mail.html());
    }

    private Role landlordRole() {
        return roleRepository.findByRoleCode("LANDLORD").orElseThrow(() ->
                new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Chưa nạp role LANDLORD, chạy db/seed.sql"));
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
