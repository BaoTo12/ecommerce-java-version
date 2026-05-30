package com.ecommerce.user.repository;

import com.ecommerce.user.model.entity.RefreshTokenFamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamilyEntity, UUID> {
    List<RefreshTokenFamilyEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
