package com.ecommerce.gateway.fliter;

import com.ecommerce.gateway.util.GatewayJwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final GatewayJwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Routes that bypass authentication
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/auth/**",
            "/actuator/health",
            "/*/v3/api-docs/**",
            "/swagger-ui/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip client forged headers unconditionally
        ServerHttpRequest cleanRequest = exchange.getRequest().mutate()
                .headers(header -> {
                    header.remove("X-User-Id");
                    header.remove("X-User-Roles");
                    header.remove("X-User-Token-Version");
                })
                .build();
        ServerWebExchange cleanExchange = exchange.mutate().request(cleanRequest).build();

        String path = cleanRequest.getURI().getPath();
        // ! is Public
        if (isPublicRoute(path)) {
            return chain.filter(cleanExchange);
        }

        // ! extract token
        String token = extractToken(cleanRequest);
        if (token == null){
            log.debug("No Bearer token on request to {}", path);
            return respondWith(cleanExchange, HttpStatus.BAD_REQUEST, "Missing token");
        }

        // ! Validate Token
        Claims claims;
        try {
             claims = jwtValidator.validateAndExtract(token);
        }catch (ExpiredJwtException e){
            log.debug("Expired token on request to {}", path);
            return respondWith(cleanExchange, HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e){
            log.warn("Invalid token on request to {}: {}", path, e.getMessage());
            return respondWith(cleanExchange, HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        // ! Add Necessary headers to request and forward downstream
        Object rolesClaim = claims.get("roles");
        if (!(rolesClaim instanceof List<?> rolesList)) {
            throw new RuntimeException("Invalid roles claim");
        }

        String roles = rolesList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining(","));

        Object tokenVersionClaim = claims.get("token_version");
        String tokenVersion = tokenVersionClaim != null ? String.valueOf(tokenVersionClaim) : "0";

        ServerHttpRequest mutatedRequest = cleanRequest.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Roles", roles)
                .header("X-User-Token-Version", tokenVersion)
                .build();

        log.debug("Authenticated userId={} roles={} version={} → {}", claims.getSubject(), roles, tokenVersion, path);

        return chain.filter(cleanExchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(route -> pathMatcher.match(route, path));
    }

    public String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")){
            return authHeader.substring(7);
        }
        return null;
    }

    private Mono<Void> respondWith(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        var body = response.bufferFactory()
                .wrap(("{\"error\":\"" + message + "\"}").getBytes());
        return response.writeWith(Mono.just(body));
    }
}
