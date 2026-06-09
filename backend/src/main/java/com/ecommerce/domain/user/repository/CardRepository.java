package com.ecommerce.domain.user.repository;

import com.ecommerce.domain.user.entity.Card;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {
    Optional<Card> findByUserIdAndIsDefaultTrue(UUID userId);
}
