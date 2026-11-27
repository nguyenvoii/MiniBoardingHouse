package com.group7.minihouse.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bao mat bat ma secret JWT yeu thi dung app ngay luc khoi dong, de thieu cau hinh khong the chay "an toan gia".
 * Chuoi mac dinh cu nam cong khai trong lich su repo, coi nhu da lo.
 */
@Component
public class SecurityStartupCheck {

    static final int MIN_SECRET_LENGTH = 32;
    static final String LEAKED_DEFAULT = "dev-only-secret-minihouse-group7-change-me-2026";

    private final boolean securityEnabled;
    private final String jwtSecret;

    public SecurityStartupCheck(@Value("${app.security.enabled:true}") boolean securityEnabled,
                                @Value("${app.jwt.secret:}") String jwtSecret) {
        this.securityEnabled = securityEnabled;
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void check() {
        validate(securityEnabled, jwtSecret);
    }

    static void validate(boolean securityEnabled, String secret) {
        if (!securityEnabled) {
            return;
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thieu app.jwt.secret (bien moi truong APP_JWT_SECRET): bao mat dang bat nen can secret JWT rieng, tu 32 ky tu tro len");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("app.jwt.secret qua ngan (" + secret.length() + " ky tu), can tu " + MIN_SECRET_LENGTH + " ky tu tro len");
        }
        if (LEAKED_DEFAULT.equals(secret)) {
            throw new IllegalStateException("app.jwt.secret dang la chuoi mac dinh cong khai trong repo, hay dat chuoi rieng");
        }
    }
}
