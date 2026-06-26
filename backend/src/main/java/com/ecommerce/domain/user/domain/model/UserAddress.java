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
public class UserAddress {
    private UUID id;
    private UUID userId;
    private String label;
    private String addressLine1;
    private String city;
    private String postalCode;
    private String country;
    private boolean isDefault;
    
    @Builder.Default
    private Instant createdAt = Instant.now();

    public void update(
            String label,
            String addressLine1,
            String city,
            String postalCode,
            String country,
            boolean isDefault) {
        this.label = label;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country != null ? country : "Vietnam";
        this.isDefault = isDefault;
    }
}
