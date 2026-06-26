package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.user.domain.model.RefreshToken;
import com.ecommerce.domain.user.domain.ports.out.RefreshTokenRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository springDataRefreshTokenRepository;
    private final SpringDataUserRepository springDataUserRepository;
    private final UserPersistenceMapper mapper;

    public RefreshTokenPersistenceAdapter(
            SpringDataRefreshTokenRepository springDataRefreshTokenRepository,
            SpringDataUserRepository springDataUserRepository,
            UserPersistenceMapper mapper) {
        this.springDataRefreshTokenRepository = springDataRefreshTokenRepository;
        this.springDataUserRepository = springDataUserRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springDataRefreshTokenRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant now) {
        springDataRefreshTokenRepository.revokeAllForUser(userId, now);
    }

    @Override
    public int deleteExpiredTokens(Instant cutoff) {
        return springDataRefreshTokenRepository.deleteExpiredTokens(cutoff);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        UserJpaEntity userJpa = springDataUserRepository.findById(refreshToken.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + refreshToken.getUser().getId()));
        RefreshTokenJpaEntity jpa = mapper.toJpa(refreshToken, userJpa);
        RefreshTokenJpaEntity saved = springDataRefreshTokenRepository.save(jpa);
        return mapper.toDomain(saved);
    }
}
