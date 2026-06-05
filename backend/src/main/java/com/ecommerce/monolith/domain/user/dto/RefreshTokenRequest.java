package com.ecommerce.monolith.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank String refreshToken, String userAgent) {
}
