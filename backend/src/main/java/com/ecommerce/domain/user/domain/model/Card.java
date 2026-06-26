package com.ecommerce.domain.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    private UUID id;
    private UUID userId;
    private String cardNumber;
    private String cvc;
    private String cardName;
    private String expiry;
    private boolean isDefault;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
}
