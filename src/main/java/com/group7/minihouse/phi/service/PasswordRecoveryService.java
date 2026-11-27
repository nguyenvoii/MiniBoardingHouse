package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.notification.NotificationSender;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC4 quen mat khau: xin ma OTP (RECOVERY) roi dat lai mat khau. Ca 2 buoc deu la API cong khai (chua dang nhap). */
@Service
public class PasswordRecoveryService {

    private static final String TYPE = "RECOVERY";
    private static final String BAD_CODE = "Ma xac thuc khong dung hoac da het han";

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
        String code;
        try {
            code = otpService.issue(user, TYPE);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                return; // vua gui ma, im lang bo qua (khong de lo tai khoan ton tai)
            }
            throw e;
        }
        String to = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getPhoneNumber();
        sender.send(to, "Ma dat lai mat khau MiniBoardingHouse",
                "Ma xac thuc cua ban la " + code + ". Ma co hieu luc 10 phut, khong chia se ma nay cho ai.");
    }

    @Transactional
    public void resetPassword(String identifier, String code, String newPassword, String ip) {
        // Kiem tra mat khau truoc khi tra tai khoan: loi nay khong phu thuoc tai khoan nen khong lo gi
        if (!AccountService.isStrong(newPassword)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mat khau moi phai co it nhat 8 ky tu, gom ca chu va so");
        }
        String id = identifier.trim();
        User user = userRepository.findByEmailOrPhoneNumber(id, id)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE));
        if (!otpService.verify(user, TYPE, code)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
        activityLogService.log(user.getUserId(), "RESET_PASSWORD", "Dat lai mat khau bang ma OTP", ip);
    }
}
