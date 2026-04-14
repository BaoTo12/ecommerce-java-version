package com.ecommerce.user.controller;

import com.ecommerce.user.model.dto.*;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(userService.updateProfile(req));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses() {
        return ResponseEntity.ok(userService.getAddresses());
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<AddressResponse> addAddress(@Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addAddress(req));
    }

    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable UUID addressId,
                                                          @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.ok(userService.updateAddress(addressId, req));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        userService.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}
