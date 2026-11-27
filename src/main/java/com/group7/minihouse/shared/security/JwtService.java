package com.group7.minihouse.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tao va doc JWT (HS256). Cau hinh: app.jwt.secret, app.jwt.expiration-minutes. */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    /**
     * secret rong (che do dev, bao mat tat): sinh ngau nhien moi lan khoi dong, token cu mat hieu luc khi restart.
     * Khi bao mat bat thi SecurityStartupCheck chan secret rong, ngan hoac la chuoi mac dinh cu.
     */
    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        String effective = secret == null || secret.isBlank()
                ? java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID() : secret;
        this.key = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(AuthUser user, String passwordHash) {
        return generate(user, passwordHash, false);
    }

    /** viaQr: phien lay tu ma QR (khach chua biet mat khau tam), cho phep dat mat khau moi ma khong can mat khau cu. */
    public String generate(AuthUser user, String passwordHash, boolean viaQr) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("credential", credentialBinding(user.userId(), passwordHash))
                .claim("via", viaQr ? QR_TYPE : null)
                .subject(String.valueOf(user.userId()))
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim("roles", user.roles())
                .claim("tenantId", user.tenantId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public record Session(AuthUser user, String id, long expiresAt, String credential, boolean viaQr) {}

    public boolean matchesPassword(Session session, String passwordHash) {
        var expected = credentialBinding(session.user().userId(), passwordHash);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                session.credential().getBytes(StandardCharsets.US_ASCII));
    }

    private String credentialBinding(Integer userId, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            byte[] digest = mac.doFinal(("stayly-session:" + userId + ":" + passwordHash)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to bind session to credentials", e);
        }
    }

    public AuthUser parse(String token) {
        Session session = parseSession(token);
        return session == null ? null : session.user();
    }

    @SuppressWarnings("unchecked")
    public Session parseSession(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String credential = c.get("credential", String.class);
            if (QR_TYPE.equals(c.get("typ", String.class)) || c.getId() == null
                    || c.getExpiration() == null || credential == null) {
                return null;
            }
            List<String> roles = c.get("roles", List.class);
            var user = new AuthUser(Integer.valueOf(c.getSubject()), c.get("email", String.class),
                    c.get("name", String.class), roles == null ? List.of() : roles, c.get("tenantId", Integer.class));
            return new Session(user, c.getId(), c.getExpiration().toInstant().getEpochSecond(), credential,
                    QR_TYPE.equals(c.get("via", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static final String QR_TYPE = "qr";

    /** Noi dung token ma QR dang nhap: nguoi thue, ho so khach thue, dau van tay cua mat khau tam luc cap. */
    public record QrClaims(Integer userId, Integer tenantId, String fingerprint) {}

    public String generateQrLogin(Integer userId, Integer tenantId, String fingerprint, long minutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", QR_TYPE)
                .claim("tenantId", tenantId)
                .claim("pwd", fingerprint)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(minutes * 60)))
                .signWith(key)
                .compact();
    }

    /** Token phien (khong phai token trong ma QR) co duoc cap tu viec quet ma QR khong. */
    public boolean isQrSession(String token) {
        Session session = parseSession(token);
        return session != null && session.viaQr();
    }

    /** Tra ve null neu token sai, het han hoac khong phai token ma QR. */
    public QrClaims parseQrLogin(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!QR_TYPE.equals(c.get("typ", String.class))) {
                return null;
            }
            return new QrClaims(Integer.valueOf(c.getSubject()), c.get("tenantId", Integer.class), c.get("pwd", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
