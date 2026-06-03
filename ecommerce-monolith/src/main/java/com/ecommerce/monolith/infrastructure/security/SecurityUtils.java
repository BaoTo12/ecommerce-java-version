package com.ecommerce.monolith.infrastructure.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helper to get the current authenticated user from the SecurityContext. Used throughout the domain
 * layer to enforce resource ownership.
 */
public final class SecurityUtils {

  private SecurityUtils() {}

  public static UUID getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new SecurityException("No authenticated user in context");
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof UUID) {
      return (UUID) principal;
    }
    return UUID.fromString(principal.toString());
  }

  public static boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(roleWithPrefix));
  }
}
