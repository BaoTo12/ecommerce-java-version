package com.ecommerce.monolith.common.security;

import com.ecommerce.monolith.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT Authentication Filter
 *
 * <p>Edge Case #15 — JWT Token Version Invalidation: When a user changes their password or logs
 * out, we increment their `token_version` in the DB. Every request validates that the JWT's
 * `token_version` claim matches the DB value. Old tokens are instantly rejected even before they
 * expire.
 *
 * <p>Edge Case #13 — CorrelationId: Reads or generates X-Correlation-Id and puts it into MDC so
 * every log line in the request carries the same trace ID.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtUtil jwtUtil;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
    this.jwtUtil = jwtUtil;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    // Set correlation ID in MDC for distributed tracing
    String correlationId = request.getHeader("X-Correlation-Id");
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString().substring(0, 8);
    }
    MDC.put("correlationId", correlationId);
    response.setHeader("X-Correlation-Id", correlationId);

    try {
      String authHeader = request.getHeader("Authorization");
      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        chain.doFilter(request, response);
        return;
      }

      String token = authHeader.substring(7);
      Claims claims;
      try {
        claims = jwtUtil.parseToken(token);
      } catch (ExpiredJwtException e) {
        log.debug("JWT expired: {}", e.getMessage());
        chain.doFilter(request, response);
        return;
      } catch (JwtException e) {
        log.warn("Invalid JWT token: {}", e.getMessage());
        chain.doFilter(request, response);
        return;
      }

      // Only process access tokens (not refresh tokens)
      String tokenType = claims.get("type", String.class);
      if (!"access".equals(tokenType)) {
        chain.doFilter(request, response);
        return;
      }

      UUID userId = UUID.fromString(claims.getSubject());
      MDC.put("userId", userId.toString());

      // ─── Edge Case #15: Token Version Validation ───────────────────────
      // Reject tokens whose version no longer matches the DB.
      // This enables instant invalidation on password change / logout.
      Integer jwtTokenVersion = claims.get("token_version", Integer.class);
      if (jwtTokenVersion != null) {
        boolean versionValid =
            userRepository.existsByIdAndTokenVersionAndIsActiveTrue(userId, jwtTokenVersion);
        if (!versionValid) {
          log.warn("Token version mismatch or user inactive for userId={}", userId);
          chain.doFilter(request, response);
          return;
        }
      }
      // ───────────────────────────────────────────────────────────────────

      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) claims.get("roles");
      List<SimpleGrantedAuthority> authorities =
          roles == null
              ? Collections.emptyList()
              : roles.stream()
                  .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                  .map(SimpleGrantedAuthority::new)
                  .collect(Collectors.toList());

      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(userId, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(auth);

      chain.doFilter(request, response);

    } finally {
      MDC.clear();
    }
  }
}
