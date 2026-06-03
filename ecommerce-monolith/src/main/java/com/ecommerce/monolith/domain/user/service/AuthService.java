package com.ecommerce.monolith.domain.user.service;

import com.ecommerce.monolith.domain.user.dto.*;
import com.ecommerce.monolith.domain.user.entity.RefreshTokenEntity;
import com.ecommerce.monolith.domain.user.entity.UserEntity;
import com.ecommerce.monolith.domain.user.repository.RefreshTokenRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;
import com.ecommerce.monolith.infrastructure.exception.BusinessRuleViolationException;
import com.ecommerce.monolith.infrastructure.exception.ResourceNotFoundException;
import com.ecommerce.monolith.infrastructure.security.JwtUtil;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication Service
 *
 * <p>Edge Case #6 — Soft Delete: login rejected for isActive=false users Edge Case #15 — Token
 * Version: bumped on password change / logout-all
 */
@Service
@Transactional
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepo;
  private final RefreshTokenRepository refreshRepo;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      UserRepository userRepo,
      RefreshTokenRepository refreshRepo,
      JwtUtil jwtUtil,
      PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.refreshRepo = refreshRepo;
    this.jwtUtil = jwtUtil;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthResponse register(RegisterRequest req) {
    if (userRepo.existsByEmail(req.email().toLowerCase())) {
      throw new BusinessRuleViolationException("Email already registered: " + req.email());
    }
    String hashed = passwordEncoder.encode(req.password());
    UserEntity user = UserEntity.create(req.email(), hashed, req.name(), req.phone());
    userRepo.save(user);
    log.info("User registered: userId={}", user.getId());
    return issueTokens(user, req.userAgent());
  }

  public AuthResponse login(LoginRequest req) {
    // Edge Case #6: reject soft-deleted users
    UserEntity user =
        userRepo
            .findByEmailAndIsActiveTrue(req.email().toLowerCase())
            .orElseThrow(() -> new BusinessRuleViolationException("Invalid credentials"));

    if (!passwordEncoder.matches(req.password(), user.getHashedPassword())) {
      log.warn("Failed login attempt for email={}", req.email());
      throw new BusinessRuleViolationException("Invalid credentials");
    }
    log.info("User logged in: userId={}", user.getId());
    return issueTokens(user, req.userAgent());
  }

  public AuthResponse refreshToken(RefreshTokenRequest req) {
    String hash = sha256(req.refreshToken());
    RefreshTokenEntity stored =
        refreshRepo
            .findByTokenHash(hash)
            .orElseThrow(() -> new BusinessRuleViolationException("Invalid refresh token"));

    if (!stored.isValid()) {
      throw new BusinessRuleViolationException("Refresh token expired or revoked");
    }

    // Validate JWT structure
    Claims claims = jwtUtil.parseToken(req.refreshToken());
    if (!"refresh".equals(claims.get("type"))) {
      throw new BusinessRuleViolationException("Not a refresh token");
    }

    UserEntity user = stored.getUser();
    if (!user.isActive()) {
      throw new BusinessRuleViolationException("Account is inactive");
    }

    // Rotate: revoke old token, issue new pair
    stored.revoke();
    refreshRepo.save(stored);

    return issueTokens(user, req.userAgent());
  }

  public void logout(String rawRefreshToken) {
    String hash = sha256(rawRefreshToken);
    refreshRepo
        .findByTokenHash(hash)
        .ifPresent(
            t -> {
              t.revoke();
              refreshRepo.save(t);
            });
  }

  /**
   * Edge Case #15: Logout from ALL devices. Increments tokenVersion → all existing access tokens
   * immediately invalid.
   */
  public void logoutAll(UUID userId) {
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    user.invalidateAllSessions(); // bumps token_version
    userRepo.save(user);
    refreshRepo.revokeAllForUser(userId, Instant.now());
    log.info("All sessions invalidated for userId={}", userId);
  }

  /** Edge Case #15: Password change invalidates all sessions. */
  public void changePassword(UUID userId, ChangePasswordRequest req) {
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

    if (!passwordEncoder.matches(req.currentPassword(), user.getHashedPassword())) {
      throw new BusinessRuleViolationException("Current password is incorrect");
    }

    user.changePassword(passwordEncoder.encode(req.newPassword())); // bumps token_version
    userRepo.save(user);
    refreshRepo.revokeAllForUser(userId, Instant.now());
    log.info("Password changed for userId={} — all sessions invalidated", userId);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private AuthResponse issueTokens(UserEntity user, String userAgent) {
    String accessToken =
        jwtUtil.generateAccessToken(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRoles(),
            user.getTokenVersion()); // Edge Case #15: include version

    String rawRefreshToken = jwtUtil.generateRefreshToken(user.getId());
    String tokenHash = sha256(rawRefreshToken);
    String deviceName = parseDeviceName(userAgent);

    RefreshTokenEntity refreshToken =
        RefreshTokenEntity.create(user, tokenHash, jwtUtil.getRefreshExpirationMs(), deviceName);
    refreshRepo.save(refreshToken);

    return new AuthResponse(
        accessToken,
        rawRefreshToken,
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRoles());
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        String h = Integer.toHexString(0xff & b);
        if (h.length() == 1) hex.append('0');
        hex.append(h);
      }
      return hex.toString();
    } catch (Exception e) {
      throw new RuntimeException("SHA-256 failed", e);
    }
  }

  private String parseDeviceName(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) return "Unknown Device";
    if (userAgent.contains("iPhone")) return "iPhone";
    if (userAgent.contains("Android")) return "Android";
    if (userAgent.contains("Mobi")) return "Mobile";
    if (userAgent.contains("Windows")) return "Windows Desktop";
    if (userAgent.contains("Macintosh")) return "macOS Desktop";
    return "Web Browser";
  }
}
