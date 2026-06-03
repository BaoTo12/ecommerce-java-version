package com.ecommerce.monolith.domain.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id, String email, String name, String phone, Instant createdAt) {}
