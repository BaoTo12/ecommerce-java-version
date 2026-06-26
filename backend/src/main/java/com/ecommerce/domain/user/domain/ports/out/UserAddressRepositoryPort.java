package com.ecommerce.domain.user.domain.ports.out;

import com.ecommerce.domain.user.domain.model.UserAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAddressRepositoryPort {
    Optional<UserAddress> findByUserIdAndIsDefaultTrue(UUID userId);
    List<UserAddress> findByUserId(UUID userId);
    UserAddress save(UserAddress address);
    Optional<UserAddress> findById(UUID id);
    void delete(UserAddress address);
}
