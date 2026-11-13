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
            List<String> roles = c.get("roles", List.class);
            return new AuthUser(Integer.valueOf(c.getSubject()), c.get("email", String.class),
                    c.get("name", String.class), roles == null ? List.of() : roles);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
