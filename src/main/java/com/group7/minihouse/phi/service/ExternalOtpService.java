package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * OTP qua dich vu ngoai (eOTP, Zalo): eOTP tu sinh va tu kiem ma (dem sai toi da 5 lan, huy ma), app chi nho request_id.
 * Vi moi ma chi xac thuc duoc 1 lan ma luong "quen mat khau" can kiem tra ma o buoc 2 roi dat mat khau o buoc 3,
 * sau khi ma dung app giu "giay phep" ngan han (cung ma) de buoc 3 dung lai ma do ma khong goi eOTP lan nua.
 * shortcut: request_id va giay phep giu trong bo nho, mat khi khoi dong lai app (nguoi dung chi can xin ma moi);
 * chay nhieu may chu thi dua vao DB (cot user_verifications.provider_ref).
 */
@Service
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "eotp")
public class ExternalOtpService {

    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final Duration GRANT_TTL = Duration.ofMinutes(10);

    private record Pending(String requestId, Instant expiresAt) {}

    private record Grant(String code, Instant expiresAt) {}

    private final EotpClient client;
    private final Clock clock;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastSent = new ConcurrentHashMap<>();

    public ExternalOtpService(EotpClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    private static String key(User user, String type) {
        return user.getUserId() + ":" + type;
    }

    /** Gui ma toi SDT cua tai khoan. Nem 429 neu vua gui chua du 60 giay. */
    public void send(User user, String type, String phone) {
        String k = key(user, type);
        Instant now = Instant.now(clock);
        Instant last = lastSent.get(k);
        if (last != null && now.isBefore(last.plus(COOLDOWN))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vừa gửi mã xác thực, vui lòng đợi 1 phút rồi thử lại");
        }
        EotpClient.Sent sent = client.send(phone);
        lastSent.put(k, now); // chi ghi nho khi gui thanh cong, loi tam thoi cua eOTP khong chan nguoi dung xin lai
        grants.remove(k);
        pending.put(k, new Pending(sent.requestId(), now.plusSeconds(sent.expiresInSeconds())));
    }

    /** Tai khoan nay dang co ma cho kiem tra hoac da kiem tra dung con han (de chon luong ngoai thay vi ma noi bo). */
    public boolean handles(User user, String type) {
        String k = key(user, type);
        Instant now = Instant.now(clock);
        Pending p = pending.get(k);
        Grant g = grants.get(k);
        return (p != null && now.isBefore(p.expiresAt())) || (g != null && now.isBefore(g.expiresAt()));
    }

    /** Hoi eOTP xem ma dung khong. Dung thi doi thanh giay phep cho buoc sau; goi lai cung ma da dung van tra true. */
    public boolean verify(User user, String type, String code) {
        String k = key(user, type);
        Instant now = Instant.now(clock);
        String entered = code == null ? "" : code.trim();
        Grant g = grants.get(k);
        if (g != null && now.isBefore(g.expiresAt()) && g.code().equals(entered)) {
            return true;
        }
        Pending p = pending.get(k);
        if (p == null || !now.isBefore(p.expiresAt())) {
            pending.remove(k);
            return false;
        }
        switch (client.verify(p.requestId(), entered)) {
            case OK:
                pending.remove(k);
                grants.put(k, new Grant(entered, now.plus(GRANT_TTL)));
                return true;
            case WRONG_CODE:
                return false; // eOTP dem so lan sai va tu huy ma khi sai 5 lan
            default: // EXPIRED, TOO_MANY, NOT_FOUND: ma khong con dung duoc
                pending.remove(k);
                return false;
        }
    }

    /** Dung giay phep (1 lan) o buoc dat mat khau moi. */
    public boolean consume(User user, String type, String code) {
        String k = key(user, type);
        Grant g = grants.get(k);
        if (g == null || !Instant.now(clock).isBefore(g.expiresAt()) || !g.code().equals(code == null ? "" : code.trim())) {
            return false;
        }
        return grants.remove(k, g);
    }
}
