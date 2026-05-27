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
        String path = exchange.getRequest().getURI().getPath();
        // ! is Public
        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        // ! extract token
        String token = extractToken(exchange.getRequest());
        if (token == null){
            log.debug("No Bearer token on request to {}", path);
            return respondWith(exchange, HttpStatus.BAD_REQUEST, "Missing token");
        }

        // ! Validate Token
        Claims claims;
        try {
             claims = jwtValidator.validateAndExtract(token);
        }catch (ExpiredJwtException e){
            log.debug("Expired token on request to {}", path);
            return respondWith(exchange, HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e){
            log.warn("Invalid token on request to {}: {}", path, e.getMessage());
            return respondWith(exchange, HttpStatus.UNAUTHORIZED, "Invalid token");
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

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(header -> {
                    header.remove("X-User-Id");
                    header.remove("X-User-Roles");
                })
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Roles", roles)
                .build();

        log.debug("Authenticated userId={} roles={} → {}", claims.getSubject(), roles, path);


        return chain.filter(exchange.mutate().request(mutatedRequest).build());
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
