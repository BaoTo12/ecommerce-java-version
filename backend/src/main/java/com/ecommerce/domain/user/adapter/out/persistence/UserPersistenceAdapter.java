package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.user.domain.model.User;
import com.ecommerce.domain.user.domain.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository springDataUserRepository;
    private final UserPersistenceMapper mapper;

    public UserPersistenceAdapter(SpringDataUserRepository springDataUserRepository, UserPersistenceMapper mapper) {
        this.springDataUserRepository = springDataUserRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findActiveById(UUID id) {
        return springDataUserRepository.findActiveById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmailAndIsActiveTrue(String email) {
        return springDataUserRepository.findByEmailAndIsActiveTrue(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataUserRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByIdAndTokenVersionAndIsActiveTrue(UUID id, int tokenVersion) {
        return springDataUserRepository.existsByIdAndTokenVersionAndIsActiveTrue(id, tokenVersion);
    }

    @Override
    public User save(User user) {
        UserJpaEntity jpa = mapper.toJpa(user);
        UserJpaEntity saved = springDataUserRepository.save(jpa);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springDataUserRepository.findById(id).map(mapper::toDomain);
    }
}
