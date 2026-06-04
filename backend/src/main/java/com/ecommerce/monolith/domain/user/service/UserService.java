package com.ecommerce.monolith.domain.user.service;

import com.ecommerce.monolith.domain.user.dto.AddressRequest;
import com.ecommerce.monolith.domain.user.dto.AddressResponse;
import com.ecommerce.monolith.domain.user.dto.UpdateProfileRequest;
import com.ecommerce.monolith.domain.user.dto.UserProfileResponse;
import com.ecommerce.monolith.domain.user.entity.UserAddressEntity;
import com.ecommerce.monolith.domain.user.entity.UserEntity;
import com.ecommerce.monolith.domain.user.repository.UserAddressRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;
import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.common.exception.ResourceOwnershipException;
import com.ecommerce.monolith.common.security.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile and address management.
 *
 * <p>Edge Case #6 — Soft Delete: deactivate instead of physical delete Edge Case #16 — Address
 * Ownership: verify address belongs to current user
 */
@Service
@Transactional
public class UserService {

  private final UserRepository userRepo;
  private final UserAddressRepository addressRepo;

  public UserService(UserRepository userRepo, UserAddressRepository addressRepo) {
    this.userRepo = userRepo;
    this.addressRepo = addressRepo;
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile() {
    UUID userId = SecurityUtils.getCurrentUserId();
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    return toProfileResponse(user);
  }

  public UserProfileResponse updateProfile(UpdateProfileRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    user.updateProfile(req.name(), req.phone());
    userRepo.save(user);
    return toProfileResponse(user);
  }

  /**
   * Edge Case #6 — Soft Delete: We don't physically delete the user. We set isActive=false and bump
   * token_version so all existing sessions are immediately invalidated. Order history, addresses,
   * payment records are preserved for auditing.
   */
  public void deleteAccount(UUID userId) {
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    user.softDelete();
    userRepo.save(user);
  }

  // ─── Addresses ────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<AddressResponse> getAddresses() {
    UUID userId = SecurityUtils.getCurrentUserId();
    return addressRepo.findByUserId(userId).stream().map(this::toAddressResponse).toList();
  }

  public AddressResponse addAddress(AddressRequest req) {
    UUID userId = SecurityUtils.getCurrentUserId();
    UserEntity user =
        userRepo
            .findActiveById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

    UserAddressEntity addr =
        UserAddressEntity.create(
            user,
            req.label(),
            req.addressLine1(),
            req.city(),
            req.postalCode(),
            req.country(),
            req.isDefault());
    addressRepo.save(addr);
    return toAddressResponse(addr);
  }

  public AddressResponse updateAddress(UUID addressId, AddressRequest req) {
    UserAddressEntity addr = loadAddressAndVerifyOwnership(addressId);
    addr.update(
        req.label(),
        req.addressLine1(),
        req.city(),
        req.postalCode(),
        req.country(),
        req.isDefault());
    addressRepo.save(addr);
    return toAddressResponse(addr);
  }

  public void deleteAddress(UUID addressId) {
    UserAddressEntity addr = loadAddressAndVerifyOwnership(addressId);
    addressRepo.delete(addr);
  }

  /**
   * Edge Case #16 — Address Ownership Check: Verifies the address belongs to the currently
   * authenticated user. Prevents users from reading/modifying/using other users' addresses. Used in
   * checkout to validate the shipping address.
   */
  public UserAddressEntity loadAddressAndVerifyOwnership(UUID addressId) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    UserAddressEntity addr =
        addressRepo
            .findById(addressId)
            .orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));

    if (!addr.getUser().getId().equals(currentUserId)) {
      throw new ResourceOwnershipException("Address", addressId);
    }
    return addr;
  }

  // ─── Mappers ──────────────────────────────────────────────────────────────

  private UserProfileResponse toProfileResponse(UserEntity u) {
    return new UserProfileResponse(
        u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getCreatedAt());
  }

  private AddressResponse toAddressResponse(UserAddressEntity a) {
    return new AddressResponse(
        a.getId(),
        a.getLabel(),
        a.getAddressLine1(),
        a.getCity(),
        a.getPostalCode(),
        a.getCountry(),
        a.isDefault());
  }
}
