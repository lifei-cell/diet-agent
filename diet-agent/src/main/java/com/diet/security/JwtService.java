package com.diet.security;

import com.diet.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${diet.security.jwt.secret}") String secret,
            @Value("${diet.security.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET 至少需要 32 字节");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_SECONDS 必须大于 0");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public JwtSubject parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        if (username == null || username.isBlank()) {
            throw new JwtException("JWT 缺少用户名声明");
        }
        return new JwtSubject(userId, username);
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public record JwtSubject(Long userId, String username) {
    }
}
