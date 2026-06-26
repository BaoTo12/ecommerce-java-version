package com.ecommerce.domain.user.adapter.in;

import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.domain.user.domain.ports.in.AuthUseCase;
import com.ecommerce.domain.user.domain.ports.in.UserUseCase;
import com.ecommerce.domain.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserUseCase userUseCase;
    private final AuthUseCase authUseCase;

    public UserController(UserUseCase userUseCase, AuthUseCase authUseCase) {
        this.userUseCase = userUseCase;
        this.authUseCase = authUseCase;
    }

    @GetMapping("/me")
    public UserProfileResponse getProfile() {
        return userUseCase.getProfile();
    }

    @PutMapping("/me")
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return userUseCase.updateProfile(req);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authUseCase.changePassword(SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authUseCase.logoutAll(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount() {
        userUseCase.deleteAccount(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/addresses")
    public List<AddressResponse> getAddresses() {
        return userUseCase.getAddresses();
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse addAddress(@Valid @RequestBody AddressRequest req) {
        return userUseCase.addAddress(req);
    }

    @PutMapping("/me/addresses/{addressId}")
    public AddressResponse updateAddress(
            @PathVariable UUID addressId, @Valid @RequestBody AddressRequest req) {
        return userUseCase.updateAddress(addressId, req);
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        userUseCase.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}
