package com.ecommerce.monolith.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Edge Case #16 — Address Ownership: Every address has an owning user. Before allowing use of an
 * address (in checkout, for example), the service must verify address.user.id == currentUserId.
 */
@Entity
@Table(
    name = "user_addresses",
    indexes = {@Index(name = "idx_address_user", columnList = "user_id")})
public class UserAddressEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 50)
  private String label; // e.g., "Home", "Office"

  @Column(name = "address_line1", nullable = false, length = 255)
  private String addressLine1;

  @Column(name = "address_line2", length = 255)
  private String addressLine2;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  @Column(name = "postal_code", length = 20)
  private String postalCode;

  @Column(nullable = false, length = 100)
  private String country;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected UserAddressEntity() {}

  public static UserAddressEntity create(
      UserEntity user,
      String label,
      String addressLine1,
      String addressLine2,
      String city,
      String state,
      String postalCode,
      String country,
      boolean isDefault) {
    UserAddressEntity a = new UserAddressEntity();
    a.user = user;
    a.label = label;
    a.addressLine1 = addressLine1;
    a.addressLine2 = addressLine2;
    a.city = city;
    a.state = state;
    a.postalCode = postalCode;
    a.country = country != null ? country : "Vietnam";
    a.isDefault = isDefault;
    a.createdAt = Instant.now();
    return a;
  }

  public void update(
      String label,
      String addressLine1,
      String addressLine2,
      String city,
      String state,
      String postalCode,
      String country,
      boolean isDefault) {
    this.label = label;
    this.addressLine1 = addressLine1;
    this.addressLine2 = addressLine2;
    this.city = city;
    this.state = state;
    this.postalCode = postalCode;
    this.country = country != null ? country : "Vietnam";
    this.isDefault = isDefault;
  }

  // ─── Getters ──────────────────────────────────────────────────────────────
  public UUID getId() {
    return id;
  }

  public UserEntity getUser() {
    return user;
  }

  public String getLabel() {
    return label;
  }

  public String getAddressLine1() {
    return addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public String getCity() {
    return city;
  }

  public String getState() {
    return state;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCountry() {
    return country;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
