package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.user.domain.model.Card;
import com.ecommerce.domain.user.domain.ports.out.CardRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CardPersistenceAdapter implements CardRepositoryPort {

    private final SpringDataCardRepository springDataCardRepository;
    private final SpringDataUserRepository springDataUserRepository;
    private final UserPersistenceMapper mapper;

    public CardPersistenceAdapter(
            SpringDataCardRepository springDataCardRepository,
            SpringDataUserRepository springDataUserRepository,
            UserPersistenceMapper mapper) {
        this.springDataCardRepository = springDataCardRepository;
        this.springDataUserRepository = springDataUserRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Card> findByUserIdAndIsDefaultTrue(UUID userId) {
        return springDataCardRepository.findByUserIdAndIsDefaultTrue(userId).map(mapper::toDomain);
    }

    @Override
    public Card save(Card card) {
        UserJpaEntity userJpa = springDataUserRepository.findById(card.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + card.getUserId()));
        CardJpaEntity jpa = mapper.toJpa(card, userJpa);
        CardJpaEntity saved = springDataCardRepository.save(jpa);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Card> findById(UUID id) {
        return springDataCardRepository.findById(id).map(mapper::toDomain);
    }
}
