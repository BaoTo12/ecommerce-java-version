package com.ecommerce.user.model.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String name,
        String accessToken,
        String refreshToken,
        String token
) {
    public AuthResponse(UUID userId, String email, String name, String accessToken, String refreshToken) {
        this(userId, email, name, accessToken, refreshToken, accessToken);
    }
}
