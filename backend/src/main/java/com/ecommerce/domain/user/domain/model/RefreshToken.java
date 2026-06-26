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
public class RefreshToken {
    private UUID id;
    private User user;
    private String tokenHash;
    private Instant expiresAt;
    private String deviceName;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    private Instant revokedAt;

    public static class RefreshTokenBuilder {
        public RefreshTokenBuilder expirationMs(long expirationMs) {
            this.expiresAt = Instant.now().plusMillis(expirationMs);
            return this;
        }
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
}
