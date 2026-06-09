package com.ecommerce.domain.user.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "user_addresses",
        indexes = {@Index(name = "idx_address_user", columnList = "user_id")})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddress {

    // ─── Getters ──────────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static class UserAddressBuilder {
        public UserAddressBuilder country(String country) {
            this.country = country != null ? country : "Vietnam";
            return this;
        }
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
