package com.ecommerce.user.repository;

import com.ecommerce.user.model.entity.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, UUID> {
    List<UserAddressEntity> findByUserId(UUID userId);
}
