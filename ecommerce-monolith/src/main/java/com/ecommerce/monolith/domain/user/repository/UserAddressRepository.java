package com.ecommerce.monolith.domain.user.repository;

import com.ecommerce.monolith.domain.user.entity.UserAddressEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, UUID> {
  List<UserAddressEntity> findByUserId(UUID userId);
}
