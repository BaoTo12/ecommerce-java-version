package com.ecommerce.monolith.domain.user.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String name,
        String roles) {
}
