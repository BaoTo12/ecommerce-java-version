package com.ecommerce.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String userRolesHeader = request.getHeader("X-User-Roles");

        if (userIdHeader != null && !userIdHeader.trim().isEmpty()) {
            List<SimpleGrantedAuthority> authorities = Collections.emptyList();
            if (userRolesHeader != null && !userRolesHeader.trim().isEmpty()) {
                authorities = Arrays.stream(userRolesHeader.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userIdHeader, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authenticated user from gateway headers: userId={}, roles={}", userIdHeader, userRolesHeader);
        }

        filterChain.doFilter(request, response);
    }
}
