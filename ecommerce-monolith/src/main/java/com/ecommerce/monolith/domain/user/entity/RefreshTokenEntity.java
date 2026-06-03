package com.ecommerce.monolith.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token stored as a SHA-256 hash. The raw token is only given to the client; we never store
 * it in plaintext.
 *
 * <p>Device tracking: we capture the User-Agent to show "Active Sessions" so the user can revoke
 * specific devices.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_user", columnList = "user_id"),
      @Index(name = "idx_refresh_hash", columnList = "token_hash")
    })
public class RefreshTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "device_name", length = 100)
  private String deviceName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected RefreshTokenEntity() {}

  public static RefreshTokenEntity create(
      UserEntity user, String tokenHash, long expirationMs, String deviceName) {
    RefreshTokenEntity t = new RefreshTokenEntity();
    t.user = user;
    t.tokenHash = tokenHash;
    t.expiresAt = Instant.now().plusMillis(expirationMs);
    t.deviceName = deviceName;
    t.createdAt = Instant.now();
    return t;
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isValid() {
    return !isExpired() && !isRevoked();
  }

  public UUID getId() {
    return id;
  }

  public UserEntity getUser() {
    return user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
