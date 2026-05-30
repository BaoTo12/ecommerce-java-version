package com.ecommerce.user.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(nullable = false, length = 255)
    private String roles = "ROLE_USER";

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserAddressEntity> addresses = new ArrayList<>();

    protected UserEntity() {}

    public static UserEntity create(String email, String passwordHash) {
        UserEntity u = new UserEntity();
        u.email = email;
        u.passwordHash = passwordHash;
        u.isActive = true;
        u.tokenVersion = 0;
        u.roles = "ROLE_USER";
        u.createdAt = Instant.now();
        u.updatedAt = Instant.now();
        return u;
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = Instant.now();
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public boolean isActive() { return isActive; }
    public int getTokenVersion() { return tokenVersion; }
    public String getRoles() { return roles; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<UserAddressEntity> getAddresses() { return addresses; }
}
