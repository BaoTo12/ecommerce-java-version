package com.ecommerce.domain.user.repository;

import com.ecommerce.domain.user.entity.User;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndIsActiveTrue(String email);

    boolean existsByEmail(String email);

    // Edge Case #15 — Token Version Validation: Called by JwtAuthenticationFilter on every
    // authenticated request. Returns true only if the user is active AND token_version matches the
    // JWT claim.
    boolean existsByIdAndTokenVersionAndIsActiveTrue(UUID id, int tokenVersion);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.isActive = true")
    Optional<User> findActiveById(UUID id);
}
