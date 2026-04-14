package com.ecommerce.user.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
                @NotBlank String label,
                @NotBlank String addressLine1,
                String addressLine2,
                @NotBlank String city,
                String state,
                @NotBlank String postalCode,
                String country,
                boolean isDefault) {
}
