package com.ecommerce.user.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_families")
public class RefreshTokenFamilyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshTokenFamilyEntity() {}

    public static RefreshTokenFamilyEntity create(UserEntity user, String deviceName, String userAgent, String ipAddress) {
        RefreshTokenFamilyEntity family = new RefreshTokenFamilyEntity();
        family.user = user;
        family.deviceName = deviceName;
        family.userAgent = userAgent;
        family.ipAddress = ipAddress;
        family.createdAt = Instant.now();
        family.updatedAt = Instant.now();
        family.lastUsedAt = Instant.now();
        return family;
    }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
        this.updatedAt = Instant.now();
    }

    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // --- Getters & Setters ---
    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getDeviceName() { return deviceName; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
