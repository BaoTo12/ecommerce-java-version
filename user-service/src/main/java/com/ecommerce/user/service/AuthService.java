package com.ecommerce.user.service;

import com.ecommerce.user.model.dto.AuthResponse;
import com.ecommerce.user.model.dto.LoginRequest;
import com.ecommerce.user.model.dto.RegisterRequest;
import com.ecommerce.user.model.entity.RefreshTokenEntity;
import com.ecommerce.user.model.entity.RefreshTokenFamilyEntity;
import com.ecommerce.user.model.entity.UserEntity;
import com.ecommerce.user.repository.RefreshTokenFamilyRepository;
import com.ecommerce.user.repository.RefreshTokenRepository;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final RefreshTokenFamilyRepository familyRepo;
    private final RefreshTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo,
                       RefreshTokenFamilyRepository familyRepo,
                       RefreshTokenRepository tokenRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.familyRepo = familyRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest req, String ip, String ua) {
        if (userRepo.existsByEmail(req.email())) {
            throw new RuntimeException("Email already registered: " + req.email());
        }

        String hash = passwordEncoder.encode(req.password());
        UserEntity user = UserEntity.create(req.email(), hash);
        userRepo.save(user);

        // Auto login on register - create family/session and RT
        RefreshTokenFamilyEntity family = RefreshTokenFamilyEntity.create(user, parseDeviceName(ua), ua, ip);
        familyRepo.save(family);

        String rawRt = jwtUtil.generateRefreshToken(user.getId());
        String rtHash = hashToken(rawRt);
        Instant rtExpires = Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs());

        RefreshTokenEntity token = RefreshTokenEntity.create(user, family, rtHash, rtExpires, ip, ua);
        tokenRepo.save(token);

        String rawAt = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRoles(), user.getTokenVersion());

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return new AuthResponse(user.getId(), user.getEmail(), user.getName(), rawAt, rawRt);
    }

    public AuthResponse login(LoginRequest req, String ip, String ua) {
        UserEntity user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Create new Refresh Token Family (session/device)
        RefreshTokenFamilyEntity family = RefreshTokenFamilyEntity.create(user, parseDeviceName(ua), ua, ip);
        familyRepo.save(family);

        // Create Refresh Token
        String rawRt = jwtUtil.generateRefreshToken(user.getId());
        String rtHash = hashToken(rawRt);
        Instant rtExpires = Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs());

        RefreshTokenEntity token = RefreshTokenEntity.create(user, family, rtHash, rtExpires, ip, ua);
        tokenRepo.save(token);

        // Create Access Token
        String rawAt = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRoles(), user.getTokenVersion());

        log.info("User logged in: id={}, email={}, session={}", user.getId(), user.getEmail(), family.getId());
        return new AuthResponse(user.getId(), user.getEmail(), user.getName(), rawAt, rawRt);
    }

    public AuthResponse refresh(String rawRt, String ip, String ua) {
        String inputHash = hashToken(rawRt);

        // SELECT FOR UPDATE to prevent race conditions (Case F6)
        RefreshTokenEntity oldToken = tokenRepo.findWithLockByTokenHash(inputHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        RefreshTokenFamilyEntity family = oldToken.getFamily();
        UserEntity user = oldToken.getUser();

        // 1. Check if family is revoked
        if (family.getRevokedAt() != null) {
            log.warn("Session reuse attempted! Family {} is already revoked.", family.getId());
            throw new RuntimeException("Invalid session");
        }

        // 2. Check if token itself is revoked (Reuse Detection! Case F4)
        if (oldToken.isRevoked()) {
            log.warn("Refresh Token reuse detected! Revoking family {} and bumping user token_version.", family.getId());
            
            // Revoke the family
            family.revoke("REFRESH_TOKEN_REUSE_DETECTED");
            familyRepo.save(family);

            // Invalidate all active tokens inside family
            List<RefreshTokenEntity> activeTokens = tokenRepo.findByFamilyIdAndRevokedAtIsNull(family.getId());
            for (RefreshTokenEntity activeToken : activeTokens) {
                activeToken.revoke("FAMILY_COMPROMISED");
            }
            tokenRepo.saveAll(activeTokens);

            // Bump token version of user to invalidate all active ATs
            user.incrementTokenVersion();
            userRepo.save(user);

            throw new RuntimeException("Compromised session. Please login again.");
        }

        // 3. Check if token is expired (Case F3)
        if (oldToken.isExpired()) {
            oldToken.revoke("EXPIRED");
            tokenRepo.save(oldToken);
            log.info("Refresh token expired for user: {}", user.getId());
            throw new RuntimeException("Refresh token expired");
        }

        // 4. Case F5: RT is valid, haven't been revoked, not expired -> Rotate!
        String newRawRt = jwtUtil.generateRefreshToken(user.getId());
        String newRtHash = hashToken(newRawRt);
        Instant newRtExpires = Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs());

        // Create new Refresh Token
        RefreshTokenEntity newRt = RefreshTokenEntity.create(user, family, newRtHash, newRtExpires, ip, ua);
        tokenRepo.save(newRt);

        // Mark old token rotated and link to new one
        oldToken.rotate(newRt.getId());
        tokenRepo.save(oldToken);

        // Update family last used time
        family.updateLastUsed();
        familyRepo.save(family);

        // Create new Access Token
        String newRawAt = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRoles(), user.getTokenVersion());

        log.debug("Session refreshed: family={}, user={}", family.getId(), user.getId());
        return new AuthResponse(user.getId(), user.getEmail(), user.getName(), newRawAt, newRawRt);
    }

    public void logout(String rawRt) {
        String inputHash = hashToken(rawRt);
        RefreshTokenEntity token = tokenRepo.findByTokenHash(inputHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        RefreshTokenFamilyEntity family = token.getFamily();
        if (family.getRevokedAt() == null) {
            family.revoke("USER_LOGOUT");
            familyRepo.save(family);

            List<RefreshTokenEntity> activeTokens = tokenRepo.findByFamilyIdAndRevokedAtIsNull(family.getId());
            for (RefreshTokenEntity activeToken : activeTokens) {
                activeToken.revoke("LOGOUT");
            }
            tokenRepo.saveAll(activeTokens);
            log.info("Logged out session: family={}", family.getId());
        }
    }

    public void logoutAll(UUID userId) {
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Revoke all active families
        List<RefreshTokenFamilyEntity> activeFamilies = familyRepo.findByUserIdAndRevokedAtIsNull(userId);
        for (RefreshTokenFamilyEntity family : activeFamilies) {
            family.revoke("LOGOUT_ALL");
            
            List<RefreshTokenEntity> activeTokens = tokenRepo.findByFamilyIdAndRevokedAtIsNull(family.getId());
            for (RefreshTokenEntity activeToken : activeTokens) {
                activeToken.revoke("LOGOUT_ALL");
            }
            tokenRepo.saveAll(activeTokens);
        }
        familyRepo.saveAll(activeFamilies);

        // Increment token version to kill all outstanding ATs
        user.incrementTokenVersion();
        userRepo.save(user);

        log.info("Logged out all devices for user: {}", userId);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private String parseDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }
        if (userAgent.contains("Mobi")) {
            if (userAgent.contains("iPhone")) return "iPhone App";
            if (userAgent.contains("Android")) return "Android App";
            return "Mobile App";
        }
        if (userAgent.contains("Windows")) return "Windows Desktop";
        if (userAgent.contains("Macintosh")) return "macOS Desktop";
        if (userAgent.contains("Linux")) return "Linux Desktop";
        return "Web Browser";
    }
}
