package com.ecommerce.monolith.domain.user.controller;

import com.ecommerce.monolith.domain.user.dto.*;
import com.ecommerce.monolith.domain.user.service.AuthService;
import com.ecommerce.monolith.domain.user.service.UserService;
import com.ecommerce.monolith.common.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final AuthService authService;

  public UserController(UserService userService, AuthService authService) {
    this.userService = userService;
    this.authService = authService;
  }

  @GetMapping("/me")
  public UserProfileResponse getProfile() {
    return userService.getProfile();
  }

  @PutMapping("/me")
  public UserProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
    return userService.updateProfile(req);
  }

  /** Edge Case #15: Change password → all sessions invalidated */
  @PostMapping("/me/change-password")
  public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
    authService.changePassword(SecurityUtils.getCurrentUserId(), req);
    return ResponseEntity.noContent().build();
  }

  /** Edge Case #15: Logout from ALL devices → all sessions invalidated */
  @PostMapping("/me/logout-all")
  public ResponseEntity<Void> logoutAll() {
    authService.logoutAll(SecurityUtils.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  /** Edge Case #6: Soft delete — preserves order history */
  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount() {
    userService.deleteAccount(SecurityUtils.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  // ─── Addresses ────────────────────────────────────────────────────────────

  @GetMapping("/me/addresses")
  public List<AddressResponse> getAddresses() {
    return userService.getAddresses();
  }

  @PostMapping("/me/addresses")
  @ResponseStatus(HttpStatus.CREATED)
  public AddressResponse addAddress(@Valid @RequestBody AddressRequest req) {
    return userService.addAddress(req);
  }

  @PutMapping("/me/addresses/{addressId}")
  public AddressResponse updateAddress(
      @PathVariable UUID addressId, @Valid @RequestBody AddressRequest req) {
    return userService.updateAddress(addressId, req);
  }

  @DeleteMapping("/me/addresses/{addressId}")
  public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
    userService.deleteAddress(addressId);
    return ResponseEntity.noContent().build();
  }
}
