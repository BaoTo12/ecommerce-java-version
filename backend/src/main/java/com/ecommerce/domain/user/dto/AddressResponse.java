package com.ecommerce.domain.user.dto;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String addressLine1,
        String city,
        String postalCode,
        String country,
        boolean isDefault) {
}
