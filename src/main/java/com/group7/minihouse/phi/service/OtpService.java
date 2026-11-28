package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserVerificationRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserVerification;
import com.group7.minihouse.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ma OTP 6 so luu o bang user_verifications, dung chung cho UC1 (dang ky), UC2 (xac thuc), UC4 (quen mat khau).
 * type: EMAIL, PHONE, RECOVERY. Ma co han 10 phut, dung 1 lan, xin lai cach nhau it nhat 60 giay.
 * Chong do ma: doan SAI MAX_WRONG (5) lan thi moi ma dang con han bi huy, phai xin ma moi (xin ma cach nhau >= 60 giay).
 */
@Service
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration COOLDOWN = Duration.ofSeconds(60);

    static final int MAX_WRONG = 5;

    // shortcut: dem so lan doan sai trong bo nho, mat khi khoi dong lai app; chay nhieu may chu thi dua vao cot DB
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> wrongGuesses =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final UserVerificationRepository repo;
    private final Clock clock;

    public OtpService(UserVerificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /**
     * Tao ma moi, huy cac ma cu. Nem 429 neu vua xin ma chua du 60 giay. Tra ve ma de caller gui di.
     * noRollbackFor: caller (quen mat khau, gui lai ma) bat 429 de im lang bo qua; neu loi nay danh dau giao dich cha
     * la rollback-only thi luc commit se no UnexpectedRollbackException (500). 429 duoc nem truoc khi ghi gi nen an toan.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public String issue(User user, String type) {
        LocalDateTime now = LocalDateTime.now(clock);
        // Ma duoc cap luc (expiresAt - TTL). Co ma nao cap trong 60 giay qua (ke ca ma da bi huy) thi chua cho xin tiep:
        // neu khong, ke tan cong co the lap "xin ma -> doan sai -> xin ma" khong gioi han.
        if (repo.countIssuedAfter(user.getUserId(), type, now.minus(COOLDOWN).plus(TTL)) > 0) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vừa gửi mã xác thực, vui lòng đợi 1 phút rồi thử lại");
        }
        // Huy ma cu bang is_verified = true (giu nguyen expires_at de con tinh duoc thoi diem cap)
        repo.findActive(user.getUserId(), type, now).forEach(v -> v.setIsVerified(true));
        wrongGuesses.remove(user.getUserId() + ":" + type); // ma moi, dem lai tu dau

        String code = String.format("%06d", random.nextInt(1_000_000));
        UserVerification v = new UserVerification();
        v.setUser(user);
        v.setType(type);
        v.setCode(code);
        v.setExpiresAt(now.plus(TTL));
        v.setIsVerified(false);
        repo.save(v);
        return code;
    }

    /** true neu code khop 1 ma con han va dung het ma do. Sai qua MAX_WRONG lan thi huy het ma con han. */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // sai ma -> caller nem loi/rollback, viec huy ma van phai luu
    public boolean verify(User user, String type, String code) {
        return check(user, type, code, true);
    }

    /** Nhu verify nhung KHONG dung het ma: de buoc 1 "nhap ma" biet ma dung roi moi cho sang buoc dat mat khau. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean check(User user, String type, String code) {
        return check(user, type, code, false);
    }

    private boolean check(User user, String type, String code, boolean consume) {
        List<UserVerification> active = repo.findActive(user.getUserId(), type, LocalDateTime.now(clock));
        if (active.isEmpty()) {
            return false;
        }
        String key = user.getUserId() + ":" + type;
        UserVerification match = code == null ? null : active.stream()
                .filter(v -> MessageDigest.isEqual(v.getCode().getBytes(StandardCharsets.UTF_8),
                        code.trim().getBytes(StandardCharsets.UTF_8)))
                .findFirst().orElse(null);
        if (match == null) {
            if (wrongGuesses.merge(key, 1, Integer::sum) >= MAX_WRONG) {
                active.forEach(v -> v.setIsVerified(true)); // het luot doan: huy ma, phai xin ma moi
                wrongGuesses.remove(key);
            }
            return false;
        }
        if (consume) {
            match.setIsVerified(true);
            wrongGuesses.remove(key);
        }
        return true;
    }
}
