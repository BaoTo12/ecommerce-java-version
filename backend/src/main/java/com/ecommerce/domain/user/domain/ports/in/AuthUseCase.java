package com.ecommerce.domain.user.domain.ports.in;

import com.ecommerce.domain.user.dto.*;

import java.util.UUID;

public interface AuthUseCase {
    AuthResponse register(RegisterRequest req);
    AuthResponse login(LoginRequest req);
    AuthResponse refreshToken(RefreshTokenRequest req);
    void logout(String rawRefreshToken);
    void logoutAll(UUID userId);
    void changePassword(UUID userId, ChangePasswordRequest req);
}
