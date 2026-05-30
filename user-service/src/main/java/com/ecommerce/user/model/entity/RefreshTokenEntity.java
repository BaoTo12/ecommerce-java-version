package com.ecommerce.user.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private RefreshTokenFamilyEntity family;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_ip", length = 45)
    private String createdByIp;

    @Column(name = "created_by_user_agent", columnDefinition = "TEXT")
    private String createdByUserAgent;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshTokenEntity() {}

    public static RefreshTokenEntity create(UserEntity user, RefreshTokenFamilyEntity family, String tokenHash,
                                            Instant expiresAt, String createdByIp, String createdByUserAgent) {
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.user = user;
        rt.family = family;
        rt.tokenHash = tokenHash;
        rt.expiresAt = expiresAt;
        rt.createdByIp = createdByIp;
        rt.createdByUserAgent = createdByUserAgent;
        rt.createdAt = Instant.now();
        return rt;
    }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public void rotate(UUID newRefreshTokenId) {
        this.revokedAt = Instant.now();
        this.revokedReason = "ROTATED";
        this.replacedByTokenId = newRefreshTokenId;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public RefreshTokenFamilyEntity getFamily() { return family; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public UUID getReplacedByTokenId() { return replacedByTokenId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedByIp() { return createdByIp; }
    public String getCreatedByUserAgent() { return createdByUserAgent; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
