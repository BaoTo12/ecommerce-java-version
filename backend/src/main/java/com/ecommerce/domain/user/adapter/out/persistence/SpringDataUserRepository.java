package com.ecommerce.domain.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmailAndIsActiveTrue(String email);

    boolean existsByEmail(String email);

    boolean existsByIdAndTokenVersionAndIsActiveTrue(UUID id, int tokenVersion);

    @Query("SELECT u FROM UserJpaEntity u WHERE u.id = :id AND u.isActive = true")
    Optional<UserJpaEntity> findActiveById(@Param("id") UUID id);
}
