package com.ecommerce.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret:7d26344377958582321b9568122abb43c2e6d49c22c883402624780149488f61}") String secret,
            @Value("${app.jwt.expiration-ms:900000}") long accessExpirationMs, // 15 mins
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) { // 7 days
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String name, String rolesStr, int tokenVersion) {
        Date now = new Date();
        List<String> roles = Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .claim("roles", roles)
                .claim("token_version", tokenVersion)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(signingKey)
                .compact();
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
