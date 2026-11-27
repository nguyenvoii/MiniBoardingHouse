package com.group7.minihouse.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tao va doc JWT (HS256). Cau hinh: app.jwt.secret, app.jwt.expiration-minutes. */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
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

    /** Tra ve AuthUser neu token hop le va chua het han, nguoc lai tra null. */
    @SuppressWarnings("unchecked")
    public AuthUser parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (QR_TYPE.equals(c.get("typ", String.class))) {
                return null; // token trong ma QR chi doi duoc lay phien dang nhap, khong dung truc tiep nhu phien
            }
            List<String> roles = c.get("roles", List.class);
            return new AuthUser(Integer.valueOf(c.getSubject()), c.get("email", String.class),
                    c.get("name", String.class), roles == null ? List.of() : roles,
                    c.get("tenantId", Integer.class));
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
