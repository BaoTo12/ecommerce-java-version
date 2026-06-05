package com.ecommerce.monolith.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "users",
    indexes = {@Index(name = "idx_users_email", columnList = "email", unique = true)})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

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

  @Setter
  @Builder.Default
  @Column(nullable = false, length = 20)
  private String roles = "USER";

  // Edge Case #15 — Token Version: Every time the user changes their password or logs out all
  // sessions, this counter is incremented. The JWT access token contains this value. The
  // JwtAuthenticationFilter validates it on every request. Stale tokens (from before the increment)
  // are rejected immediately.
  @Builder.Default
  @Column(name = "token_version", nullable = false)
  private int tokenVersion = 0;

  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Builder.Default
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Builder.Default
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<UserAddress> addresses = new ArrayList<>();

  public static class UserBuilder {
    public UserBuilder email(String email) {
      this.email = email != null ? email.toLowerCase().trim() : null;
      return this;
    }
  }

  public void updateProfile(String name, String phone) {
    this.name = name;
    this.phone = phone;
    this.updatedAt = Instant.now();
  }

  public void changePassword(String newHashedPassword) {
    this.hashedPassword = newHashedPassword;
    this.tokenVersion++; // ← all existing JWTs instantly invalidated
    this.updatedAt = Instant.now();
  }

  // Edge Case #15: Force logout all sessions by bumping the token version.
  public void invalidateAllSessions() {
    this.tokenVersion++;
    this.updatedAt = Instant.now();
  }

  // Edge Case #6: Soft delete — preserves FK integrity and order history.
  public void softDelete() {
    this.isActive = false;
    this.tokenVersion++; // also invalidate all sessions
    this.updatedAt = Instant.now();
  }
}
