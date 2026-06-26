package com.ecommerce.domain.user.domain.ports.out;

import com.ecommerce.domain.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    Optional<User> findActiveById(UUID id);
    Optional<User> findByEmailAndIsActiveTrue(String email);
    boolean existsByEmail(String email);
    boolean existsByIdAndTokenVersionAndIsActiveTrue(UUID id, int tokenVersion);
    User save(User user);
    Optional<User> findById(UUID id);
}
