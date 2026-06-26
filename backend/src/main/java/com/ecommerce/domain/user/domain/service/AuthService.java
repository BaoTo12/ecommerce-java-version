package com.ecommerce.domain.user.domain.service;

import com.ecommerce.common.exception.BusinessRuleViolationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.security.JwtUtil;
import com.ecommerce.domain.user.domain.model.RefreshToken;
import com.ecommerce.domain.user.domain.model.User;
import com.ecommerce.domain.user.domain.ports.in.AuthUseCase;
import com.ecommerce.domain.user.domain.ports.out.RefreshTokenRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.UserRepositoryPort;
import com.ecommerce.domain.user.dto.*;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepositoryPort userRepo;
    private final RefreshTokenRepositoryPort refreshRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepositoryPort userRepo,
            RefreshTokenRepositoryPort refreshRepo,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email().toLowerCase())) {
            throw new BusinessRuleViolationException("Email already registered: " + req.email());
        }
        String hashed = passwordEncoder.encode(req.password());
        User user = User.builder()
                .email(req.email())
                .hashedPassword(hashed)
                .name(req.name())
                .phone(req.phone())
                .build();
        user = userRepo.save(user);
        log.info("User registered: userId={}", user.getId());
        return issueTokens(user, req.userAgent());
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        User user = userRepo
                .findByEmailAndIsActiveTrue(req.email().toLowerCase())
                .orElseThrow(() -> new BusinessRuleViolationException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getHashedPassword())) {
            log.warn("Failed login attempt for email={}", req.email());
            throw new BusinessRuleViolationException("Invalid credentials");
        }
        log.info("User logged in: userId={}", user.getId());
        return issueTokens(user, req.userAgent());
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String hash = sha256(req.refreshToken());
        RefreshToken stored = refreshRepo
                .findByTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleViolationException("Invalid refresh token"));

        if (!stored.isValid()) {
            throw new BusinessRuleViolationException("Refresh token expired or revoked");
        }

        Claims claims = jwtUtil.parseToken(req.refreshToken());
        if (!"refresh".equals(claims.get("type"))) {
            throw new BusinessRuleViolationException("Not a refresh token");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new BusinessRuleViolationException("Account is inactive");
        }

        stored.revoke();
        refreshRepo.save(stored);

        return issueTokens(user, req.userAgent());
    }

    @Override
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshRepo
                .findByTokenHash(hash)
                .ifPresent(t -> {
                    t.revoke();
                    refreshRepo.save(t);
                });
    }

    @Override
    public void logoutAll(UUID userId) {
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.invalidateAllSessions();
        userRepo.save(user);
        refreshRepo.revokeAllForUser(userId, Instant.now());
        log.info("All sessions invalidated for userId={}", userId);
    }

    @Override
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (!passwordEncoder.matches(req.currentPassword(), user.getHashedPassword())) {
            throw new BusinessRuleViolationException("Current password is incorrect");
        }

        user.changePassword(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);
        refreshRepo.revokeAllForUser(userId, Instant.now());
        log.info("Password changed for userId={} — all sessions invalidated", userId);
    }

    private AuthResponse issueTokens(User user, String userAgent) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRoles(),
                user.getTokenVersion());

        String rawRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        String tokenHash = sha256(rawRefreshToken);
        String deviceName = parseDeviceName(userAgent);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expirationMs(jwtUtil.getRefreshExpirationMs())
                .deviceName(deviceName)
                .build();
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
