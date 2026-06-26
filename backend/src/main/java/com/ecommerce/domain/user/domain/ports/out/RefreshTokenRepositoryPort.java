package com.ecommerce.domain.user.domain.ports.out;

import com.ecommerce.domain.user.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revokeAllForUser(UUID userId, Instant now);
    int deleteExpiredTokens(Instant cutoff);
    RefreshToken save(RefreshToken refreshToken);
}
