package com.ecommerce.monolith.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT Utility
 *
 * <p>Edge Case #15 — Token Version: Access tokens embed a `token_version` claim. When the user
 * changes their password or explicitly logs out all sessions, we increment the version in the DB.
 * The JwtAuthenticationFilter compares claim vs DB value and rejects stale tokens immediately — no
 * need to wait for natural expiry.
 */
@Component
public class JwtUtil {

  private final SecretKey signingKey;
  private final long accessExpirationMs;
  private final long refreshExpirationMs;

  public JwtUtil(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,
      @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  /**
   * Generates a short-lived access token (15 min by default). Includes token_version for instant
   * revocation support.
   */
  public String generateAccessToken(
      UUID userId, String email, String name, String rolesStr, int tokenVersion) {
    Date now = new Date();
    List<String> roles =
        Arrays.stream(rolesStr.split(","))
            .map(String::trim)
            .filter(r -> !r.isEmpty())
            .collect(Collectors.toList());

    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("name", name)
        .claim("roles", roles)
        .claim("token_version", tokenVersion) // Edge Case #15
        .claim("type", "access")
        .issuedAt(now)
        .expiration(new Date(now.getTime() + accessExpirationMs))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Generates a long-lived refresh token (7 days by default). Stored as a SHA-256 hash in the DB —
   * actual token only given to the client.
   */
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
    return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
  }
}
