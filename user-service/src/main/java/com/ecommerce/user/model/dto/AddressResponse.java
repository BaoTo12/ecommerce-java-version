package com.ecommerce.user.model.dto;

import java.util.UUID;

public record AddressResponse(UUID id, String label, String addressLine1, String addressLine2,
                                String city, String state, String postalCode, String country, boolean isDefault) {}
