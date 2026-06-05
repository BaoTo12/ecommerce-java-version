package com.ecommerce.monolith.domain.user.repository;

import com.ecommerce.monolith.domain.user.entity.UserAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {
  Optional<UserAddress> findByUserIdAndIsDefaultTrue(UUID userId);

  List<UserAddress> findByUserId(UUID userId);
}
