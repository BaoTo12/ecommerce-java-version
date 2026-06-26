package com.ecommerce.domain.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserAddressRepository extends JpaRepository<UserAddressJpaEntity, UUID> {
    Optional<UserAddressJpaEntity> findByUserIdAndIsDefaultTrue(UUID userId);
    List<UserAddressJpaEntity> findByUserId(UUID userId);
}
