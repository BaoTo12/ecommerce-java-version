package com.ecommerce.user.controller;

import com.ecommerce.shared.security.SecurityUtils;
import com.ecommerce.user.model.dto.AuthResponse;
import com.ecommerce.user.model.dto.LoginRequest;
import com.ecommerce.user.model.dto.RegisterRequest;
import com.ecommerce.user.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        String ip = getClientIp(request);
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse res = authService.register(req, ip, ua);
        setRefreshTokenCookie(response, res.refreshToken(), 7 * 24 * 60 * 60); // 7 days
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        String ip = getClientIp(request);
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse res = authService.login(req, ip, ua);
        setRefreshTokenCookie(response, res.refreshToken(), 7 * 24 * 60 * 60); // 7 days
        return ResponseEntity.ok(res);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token", required = false) String cookieRt,
                                                @RequestBody(required = false) Map<String, String> body,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        String rt = cookieRt;
        if (rt == null && body != null) {
            rt = body.get("refreshToken");
        }

        if (rt == null) {
            throw new RuntimeException("Missing refresh token");
        }

        String ip = getClientIp(request);
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse res = authService.refresh(rt, ip, ua);
        setRefreshTokenCookie(response, res.refreshToken(), 7 * 24 * 60 * 60);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String cookieRt,
                                       @RequestBody(required = false) Map<String, String> body,
                                       HttpServletResponse response) {
        String rt = cookieRt;
        if (rt == null && body != null) {
            rt = body.get("refreshToken");
        }

        if (rt != null) {
            authService.logout(rt);
        }
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletResponse response) {
        authService.logoutAll(SecurityUtils.getCurrentUserId());
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSec) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false) // Set to false so it works in non-HTTPS local dev environments
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSec)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
