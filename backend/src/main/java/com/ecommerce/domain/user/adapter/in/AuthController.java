package com.ecommerce.domain.user.adapter.in;

import com.ecommerce.common.resilience.RateLimit;
import com.ecommerce.domain.user.domain.ports.in.AuthUseCase;
import com.ecommerce.domain.user.dto.AuthResponse;
import com.ecommerce.domain.user.dto.LoginRequest;
import com.ecommerce.domain.user.dto.RefreshTokenRequest;
import com.ecommerce.domain.user.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return authUseCase.register(withUserAgent(req, http));
    }

    @PostMapping("/login")
    @RateLimit(maxRequests = 20, windowSeconds = 60, name = "login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authUseCase.login(withUserAgent(req, http));
    }

    @PostMapping("/refresh")
    @RateLimit(maxRequests = 30, windowSeconds = 60, name = "token-refresh")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshTokenRequest req, HttpServletRequest http) {
        return authUseCase.refreshToken(withUserAgent(req, http));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest req) {
        authUseCase.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

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
