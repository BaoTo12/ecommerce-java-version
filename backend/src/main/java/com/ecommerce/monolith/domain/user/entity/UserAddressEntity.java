package com.ecommerce.monolith.domain.user.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Edge Case #16 — Address Ownership: Every address has an owning user. Before allowing use of an
 * address (in checkout, for example), the service must verify address.user.id == currentUserId.
 */
@Getter
@Entity
@Table(
    name = "user_addresses",
    indexes = {@Index(name = "idx_address_user", columnList = "user_id")})
public class UserAddressEntity {

    // ─── Getters ──────────────────────────────────────────────────────────────
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


  @Column(nullable = false, length = 100)
  private String city;

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
      String city,
      String postalCode,
      String country,
      boolean isDefault) {
    UserAddressEntity a = new UserAddressEntity();
    a.user = user;
    a.label = label;
    a.addressLine1 = addressLine1;
    a.city = city;
    a.postalCode = postalCode;
    a.country = country != null ? country : "Vietnam";
    a.isDefault = isDefault;
    a.createdAt = Instant.now();
    return a;
  }

  public void update(
      String label,
      String addressLine1,
      String city,
      String postalCode,
      String country,
      boolean isDefault) {
    this.label = label;
    this.addressLine1 = addressLine1;
    this.city = city;
    this.postalCode = postalCode;
    this.country = country != null ? country : "Vietnam";
    this.isDefault = isDefault;
  }

}
