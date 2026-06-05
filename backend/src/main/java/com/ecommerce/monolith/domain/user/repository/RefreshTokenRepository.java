package com.ecommerce.monolith.domain.user.repository;

import com.ecommerce.monolith.domain.user.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
  void revokeAllForUser(UUID userId, Instant now);

  @Modifying
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
  int deleteExpiredTokens(Instant cutoff);
}
