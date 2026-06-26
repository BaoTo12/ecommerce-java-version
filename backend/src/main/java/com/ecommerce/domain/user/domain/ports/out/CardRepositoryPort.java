package com.ecommerce.domain.user.domain.ports.out;

import com.ecommerce.domain.user.domain.model.Card;
import java.util.Optional;
import java.util.UUID;

public interface CardRepositoryPort {
    Optional<Card> findByUserIdAndIsDefaultTrue(UUID userId);
    Card save(Card card);
    Optional<Card> findById(UUID id);
}
