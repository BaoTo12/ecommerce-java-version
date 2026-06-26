package com.ecommerce.domain.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String hashedPassword;
    private String name;
    private String phone;
    
    @Builder.Default
    private String roles = "USER";
    
    @Builder.Default
    private int tokenVersion = 0;
    
    @Builder.Default
    private boolean isActive = true;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newHashedPassword) {
        this.hashedPassword = newHashedPassword;
        this.tokenVersion++;
        this.updatedAt = Instant.now();
    }

    public void invalidateAllSessions() {
        this.tokenVersion++;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.isActive = false;
        this.tokenVersion++;
        this.updatedAt = Instant.now();
    }
}
