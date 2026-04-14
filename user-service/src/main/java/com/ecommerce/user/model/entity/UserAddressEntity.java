package com.ecommerce.user.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
public class UserAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 50)
    private String label = "Home";

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country = "Vietnam";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAddressEntity() {}

    public static UserAddressEntity create(UserEntity user, String label, String line1, String line2,
                                            String city, String state, String postalCode, String country,
                                            boolean isDefault) {
        UserAddressEntity a = new UserAddressEntity();
        a.user = user;
        a.label = label;
        a.addressLine1 = line1;
        a.addressLine2 = line2;
        a.city = city;
        a.state = state;
        a.postalCode = postalCode;
        a.country = country;
        a.isDefault = isDefault;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(String label, String line1, String line2, String city,
                       String state, String postalCode, String country, boolean isDefault) {
        this.label = label;
        this.addressLine1 = line1;
        this.addressLine2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.isDefault = isDefault;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getLabel() { return label; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public boolean isDefault() { return isDefault; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
