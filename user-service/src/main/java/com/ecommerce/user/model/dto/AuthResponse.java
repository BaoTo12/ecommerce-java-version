package com.ecommerce.user.model.dto;

import java.util.UUID;

public record AuthResponse(UUID userId, String email, String name, String token) {}
