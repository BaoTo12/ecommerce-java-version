package com.ecommerce.domain.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCardRepository extends JpaRepository<CardJpaEntity, UUID> {
    Optional<CardJpaEntity> findByUserIdAndIsDefaultTrue(UUID userId);
}
