package com.ecommerce.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class GatewayJwtValidator {

    private final SecretKey jwtSecret;

    public GatewayJwtValidator(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validateAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"access".equals(claims.get("type", String.class))) {
            throw new JwtException("Token is not an access token");
        }
        return claims;
    }
}
