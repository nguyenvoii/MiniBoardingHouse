package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.notification.NotificationSender;
import com.group7.minihouse.shared.notification.OtpMail;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC4 quen mat khau: xin ma OTP (RECOVERY) roi dat lai mat khau. Ca 2 buoc deu la API cong khai (chua dang nhap). */
@Service
public class PasswordRecoveryService {

    private static final String TYPE = "RECOVERY";
    private static final String BAD_CODE = "Mã xác thực không đúng hoặc đã hết hạn";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordRecoveryService.class);

    /** Co khi app.otp.provider=eotp: nguoi dung nhap SO DIEN THOAI thi ma gui qua Zalo (eOTP), nhap email thi gui email nhu cu. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    ExternalOtpService external;

    /** true: nguoi dung nhap SO DIEN THOAI o man quen mat khau thi ma gui bang SMS (Twilio, can app.sms.enabled=true). Mac dinh gui email. */
    @org.springframework.beans.factory.annotation.Value("${app.otp.sms-on-phone:false}")
    boolean smsOnPhone;

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final NotificationSender sender;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public PasswordRecoveryService(UserRepository userRepository, OtpService otpService, NotificationSender sender,
                                   PasswordEncoder passwordEncoder, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.sender = sender;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    /**
     * Gui ma ve email (neu tai khoan co email) hoac SDT. KHONG bao gio bao loi du tai khoan co ton tai hay khong,
     * de nguoi la khong do duoc tai khoan; controller luon tra cung 1 thong bao.
     */
    @Transactional
    public void forgotPassword(String identifier, String ip) {
        String id = identifier.trim();
        User user = userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> "ACTIVE".equals(u.getStatus())).orElse(null);
        if (user == null) {
            return;
        }
        if (external != null && user.getPhoneNumber() != null && id.equals(user.getPhoneNumber())) {
            try {
                external.send(user, TYPE, user.getPhoneNumber());
            } catch (BusinessException e) {
                // vua gui ma hoac dich vu gui loi: im lang (khong de lo tai khoan ton tai), chi ghi log
                log.warn("Khong gui duoc ma Zalo cho tai khoan {}: {}", user.getUserId(), e.getMessage());
            }
            return;
        }
        String code;
        try {
            code = otpService.issue(user, TYPE);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                return; // vua gui ma, im lang bo qua (khong de lo tai khoan ton tai)
            }
            throw e;
        }
        // Nhap SDT + bat app.otp.sms-on-phone: gui SMS (Twilio) toi SDT do; con lai gui email, tai khoan khong co email thi SMS.
        boolean viaSms = smsOnPhone && user.getPhoneNumber() != null && id.equals(user.getPhoneNumber());
        String to = viaSms ? user.getPhoneNumber()
                : user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getPhoneNumber();
        if (to.contains("@")) {
            OtpMail mail = OtpMail.recovery(code);
            sender.sendHtml(to, mail.subject(), mail.text(), mail.html());
            return;
        }
        try {
            sender.send(to, "Ma dat lai mat khau Stayly", OtpMail.recoverySms(code));
        } catch (BusinessException e) {
            // SMS loi (Twilio het tien, so khong hop le...): im lang de khong lo tai khoan ton tai, chi ghi log
            log.warn("Khong gui duoc SMS ma dat lai mat khau cho tai khoan {}: {}", user.getUserId(), e.getMessage());
        }
    }

    /** Buoc 2: kiem tra ma (chua dung het ma) de giao dien biet cho phep sang buoc nhap mat khau moi. */
    @Transactional
    public void verifyCode(String identifier, String code) {
        String id = identifier.trim();
        User user = userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        boolean ok = external != null && external.handles(user, TYPE)
                ? external.verify(user, TYPE, code) : otpService.check(user, TYPE, code);
        if (!ok) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
    }

    @Transactional
    public void resetPassword(String identifier, String code, String newPassword, String ip) {
        // Kiem tra mat khau truoc khi tra tai khoan: loi nay khong phu thuoc tai khoan nen khong lo gi
        if (!AccountService.isStrong(newPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới phải có ít nhất 8 ký tự, gồm cả chữ và số");
        }
        String id = identifier.trim();
        User user = userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        // luong eOTP: ma da duoc kiem o buoc 2 (eOTP chi cho kiem 1 lan) nen o day dung "giay phep" cua buoc 2
        boolean ok = external != null && external.handles(user, TYPE)
                ? external.consume(user, TYPE, code) : otpService.verify(user, TYPE, code);
        if (!ok) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        activityLogService.log(user.getUserId(), "RESET_PASSWORD", "Dat lai mat khau bang ma OTP", ip);
    }
}
