package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.user.domain.model.Card;
import com.ecommerce.domain.user.domain.model.RefreshToken;
import com.ecommerce.domain.user.domain.model.User;
import com.ecommerce.domain.user.domain.model.UserAddress;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class UserPersistenceMapper {

    public User toDomain(UserJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return User.builder()
                .id(jpa.getId())
                .email(jpa.getEmail())
                .hashedPassword(jpa.getHashedPassword())
                .name(jpa.getName())
                .phone(jpa.getPhone())
                .roles(jpa.getRoles())
                .tokenVersion(jpa.getTokenVersion())
                .isActive(jpa.isActive())
                .createdAt(jpa.getCreatedAt())
                .updatedAt(jpa.getUpdatedAt())
                .build();
    }

    public UserJpaEntity toJpa(User domain) {
        if (domain == null) {
            return null;
        }
        return UserJpaEntity.builder()
                .id(domain.getId())
                .email(domain.getEmail())
                .hashedPassword(domain.getHashedPassword())
                .name(domain.getName())
                .phone(domain.getPhone())
                .roles(domain.getRoles())
                .tokenVersion(domain.getTokenVersion())
                .isActive(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .addresses(new ArrayList<>())
                .build();
    }

    public UserAddress toDomain(UserAddressJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return UserAddress.builder()
                .id(jpa.getId())
                .userId(jpa.getUser() != null ? jpa.getUser().getId() : null)
                .label(jpa.getLabel())
                .addressLine1(jpa.getAddressLine1())
                .city(jpa.getCity())
                .postalCode(jpa.getPostalCode())
                .country(jpa.getCountry())
                .isDefault(jpa.isDefault())
                .createdAt(jpa.getCreatedAt())
                .build();
    }

    public UserAddressJpaEntity toJpa(UserAddress domain, UserJpaEntity userJpa) {
        if (domain == null) {
            return null;
        }
        return UserAddressJpaEntity.builder()
                .id(domain.getId())
                .user(userJpa)
                .label(domain.getLabel())
                .addressLine1(domain.getAddressLine1())
                .city(domain.getCity())
                .postalCode(domain.getPostalCode())
                .country(domain.getCountry())
                .isDefault(domain.isDefault())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public Card toDomain(CardJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return Card.builder()
                .id(jpa.getId())
                .userId(jpa.getUser() != null ? jpa.getUser().getId() : null)
                .cardNumber(jpa.getCardNumber())
                .cvc(jpa.getCvc())
                .cardName(jpa.getCardName())
                .expiry(jpa.getExpiry())
                .isDefault(jpa.isDefault())
                .createdAt(jpa.getCreatedAt())
                .build();
    }

    public CardJpaEntity toJpa(Card domain, UserJpaEntity userJpa) {
        if (domain == null) {
            return null;
        }
        return CardJpaEntity.builder()
                .id(domain.getId())
                .user(userJpa)
                .cardNumber(domain.getCardNumber())
                .cvc(domain.getCvc())
                .cardName(domain.getCardName())
                .expiry(domain.getExpiry())
                .isDefault(domain.isDefault())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public RefreshToken toDomain(RefreshTokenJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return RefreshToken.builder()
                .id(jpa.getId())
                .user(toDomain(jpa.getUser()))
                .tokenHash(jpa.getTokenHash())
                .expiresAt(jpa.getExpiresAt())
                .deviceName(jpa.getDeviceName())
                .createdAt(jpa.getCreatedAt())
                .revokedAt(jpa.getRevokedAt())
                .build();
    }

    public RefreshTokenJpaEntity toJpa(RefreshToken domain, UserJpaEntity userJpa) {
        if (domain == null) {
            return null;
        }
        return RefreshTokenJpaEntity.builder()
                .id(domain.getId())
                .user(userJpa)
                .tokenHash(domain.getTokenHash())
                .expiresAt(domain.getExpiresAt())
                .deviceName(domain.getDeviceName())
                .createdAt(domain.getCreatedAt())
                .revokedAt(domain.getRevokedAt())
                .build();
    }
}
