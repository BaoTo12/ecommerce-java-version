package com.ecommerce.monolith.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User entity
 *
 * <p>Edge Case #6 — Soft Delete: is_active flag. Deleted users keep their order history. Edge Case
 * #15 — Token Version: token_version incremented on password change / logout-all.
 */
@Entity
@Table(
    name = "users",
    indexes = {@Index(name = "idx_users_email", columnList = "email", unique = true)})
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "hashed_password", nullable = false)
  private String hashedPassword;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false, length = 20)
  private String roles = "USER";

  /**
   * Edge Case #15 — Token Version: Every time the user changes their password or logs out all
   * sessions, this counter is incremented. The JWT access token contains this value. The
   * JwtAuthenticationFilter validates it on every request. Stale tokens (from before the increment)
   * are rejected immediately.
   */
  @Column(name = "token_version", nullable = false)
  private int tokenVersion = 0;

  /**
   * Edge Case #6 — Soft Delete: Instead of physically deleting the user (which would break FK
   * constraints on orders, carts, etc.), we set isActive = false. - Login is rejected for inactive
   * users. - Orders are preserved for audit/legal purposes. - The user's PII can be anonymized
   * separately (GDPR compliance).
   */
  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<UserAddressEntity> addresses = new ArrayList<>();

  protected UserEntity() {}

  public static UserEntity create(String email, String hashedPassword, String name, String phone) {
    UserEntity u = new UserEntity();
    u.email = email.toLowerCase().trim();
    u.hashedPassword = hashedPassword;
    u.name = name;
    u.phone = phone;
    u.createdAt = Instant.now();
    u.updatedAt = Instant.now();
    return u;
  }

  public void updateProfile(String name, String phone) {
    this.name = name;
    this.phone = phone;
    this.updatedAt = Instant.now();
  }

  /**
   * Edge Case #15: Changing password invalidates all existing sessions. Old access tokens with the
   * previous token_version are rejected.
   */
  public void changePassword(String newHashedPassword) {
    this.hashedPassword = newHashedPassword;
    this.tokenVersion++; // ← all existing JWTs instantly invalidated
    this.updatedAt = Instant.now();
  }

  /** Edge Case #15: Force logout all sessions by bumping the token version. */
  public void invalidateAllSessions() {
    this.tokenVersion++;
    this.updatedAt = Instant.now();
  }

  /** Edge Case #6: Soft delete — preserves FK integrity and order history. */
  public void softDelete() {
    this.isActive = false;
    this.tokenVersion++; // also invalidate all sessions
    this.updatedAt = Instant.now();
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getHashedPassword() {
    return hashedPassword;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public String getRoles() {
    return roles;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public boolean isActive() {
    return isActive;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<UserAddressEntity> getAddresses() {
    return addresses;
  }
}
