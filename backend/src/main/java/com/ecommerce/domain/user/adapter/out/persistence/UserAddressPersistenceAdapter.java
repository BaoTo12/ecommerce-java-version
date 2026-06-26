package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.user.domain.model.UserAddress;
import com.ecommerce.domain.user.domain.ports.out.UserAddressRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserAddressPersistenceAdapter implements UserAddressRepositoryPort {

    private final SpringDataUserAddressRepository springDataUserAddressRepository;
    private final SpringDataUserRepository springDataUserRepository;
    private final UserPersistenceMapper mapper;

    public UserAddressPersistenceAdapter(
            SpringDataUserAddressRepository springDataUserAddressRepository,
            SpringDataUserRepository springDataUserRepository,
            UserPersistenceMapper mapper) {
        this.springDataUserAddressRepository = springDataUserAddressRepository;
        this.springDataUserRepository = springDataUserRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<UserAddress> findByUserIdAndIsDefaultTrue(UUID userId) {
        return springDataUserAddressRepository.findByUserIdAndIsDefaultTrue(userId).map(mapper::toDomain);
    }

    @Override
    public List<UserAddress> findByUserId(UUID userId) {
        return springDataUserAddressRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public UserAddress save(UserAddress address) {
        UserJpaEntity userJpa = springDataUserRepository.findById(address.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + address.getUserId()));
        UserAddressJpaEntity jpa = mapper.toJpa(address, userJpa);
        UserAddressJpaEntity saved = springDataUserAddressRepository.save(jpa);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserAddress> findById(UUID id) {
        return springDataUserAddressRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public void delete(UserAddress address) {
        springDataUserAddressRepository.deleteById(address.getId());
    }
}
