package com.ecommerce.user.config;

import com.ecommerce.user.model.entity.UserEntity;
import com.ecommerce.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class UserTokenVersionFilter extends OncePerRequestFilter {

    private final UserRepository userRepo;

    public UserTokenVersionFilter(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            String tokenVersionHeader = request.getHeader("X-User-Token-Version");
            
            if (tokenVersionHeader != null) {
                try {
                    UUID userId = UUID.fromString(auth.getPrincipal().toString());
                    int tokenVersion = Integer.parseInt(tokenVersionHeader);

                    UserEntity user = userRepo.findById(userId).orElse(null);
                    if (user == null || user.getTokenVersion() != tokenVersion || !user.isActive()) {
                        // Token version mismatch or user inactive! Revoke authentication and reject!
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"ACCESS_TOKEN_EXPIRED\",\"message\":\"Force logout - token version mismatch\"}");
                        return;
                    }
                } catch (Exception e) {
                    // Invalid format or other error - reject request
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid authentication headers\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
