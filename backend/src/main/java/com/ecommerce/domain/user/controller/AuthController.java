package com.ecommerce.domain.user.controller;

import com.ecommerce.common.resilience.RateLimit;
import com.ecommerce.domain.user.dto.AuthResponse;
import com.ecommerce.domain.user.dto.LoginRequest;
import com.ecommerce.domain.user.dto.RefreshTokenRequest;
import com.ecommerce.domain.user.dto.RegisterRequest;
import com.ecommerce.monolith.domain.user.dto.*;
import com.ecommerce.domain.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return authService.register(withUserAgent(req, http));
    }

    @PostMapping("/login")
    @RateLimit(maxRequests = 20, windowSeconds = 60, name = "login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(withUserAgent(req, http));
    }

    @PostMapping("/refresh")
    @RateLimit(maxRequests = 30, windowSeconds = 60, name = "token-refresh")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshTokenRequest req, HttpServletRequest http) {
        return authService.refreshToken(withUserAgent(req, http));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private RegisterRequest withUserAgent(RegisterRequest req, HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        return new RegisterRequest(req.email(), req.password(), req.name(), req.phone(), ua);
    }

    private LoginRequest withUserAgent(LoginRequest req, HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        return new LoginRequest(req.email(), req.password(), ua);
    }

    private RefreshTokenRequest withUserAgent(RefreshTokenRequest req, HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        return new RefreshTokenRequest(req.refreshToken(), ua);
    }
}
