package com.ecommerce.monolith.domain.user.repository;

import com.ecommerce.monolith.domain.user.entity.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
  void revokeAllForUser(UUID userId, Instant now);

  @Modifying
  @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff")
  int deleteExpiredTokens(Instant cutoff);
}
