package com.ecommerce.monolith.domain.user.service;

import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.common.exception.ResourceOwnershipException;
import com.ecommerce.monolith.common.security.SecurityUtils;
import com.ecommerce.monolith.domain.user.dto.AddressRequest;
import com.ecommerce.monolith.domain.user.dto.AddressResponse;
import com.ecommerce.monolith.domain.user.dto.UpdateProfileRequest;
import com.ecommerce.monolith.domain.user.dto.UserProfileResponse;
import com.ecommerce.monolith.domain.user.entity.User;
import com.ecommerce.monolith.domain.user.entity.UserAddress;
import com.ecommerce.monolith.domain.user.repository.UserAddressRepository;
import com.ecommerce.monolith.domain.user.repository.UserRepository;
import com.ecommerce.monolith.domain.user.mapper.UserMapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class UserService {

    private final UserRepository userRepo;
    private final UserAddressRepository addressRepo;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepo, UserAddressRepository addressRepo, UserMapper userMapper) {
        this.userRepo = userRepo;
        this.addressRepo = addressRepo;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepo
                        .findActiveById(userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        return userMapper.toProfileResponse(user);
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepo
                        .findActiveById(userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.updateProfile(req.name(), req.phone());
        userRepo.save(user);
        return userMapper.toProfileResponse(user);
    }

    /**
     * Edge Case #6 — Soft Delete: We don't physically delete the user. We set isActive=false and bump
     * token_version so all existing sessions are immediately invalidated. Order history, addresses,
     * payment records are preserved for auditing.
     */
    public void deleteAccount(UUID userId) {
        User user =
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
        return addressRepo.findByUserId(userId).stream().map(userMapper::toAddressResponse).toList();
    }

    public AddressResponse addAddress(AddressRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepo
                        .findActiveById(userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        UserAddress addr =
                UserAddress.builder()
                        .user(user)
                        .label(req.label())
                        .addressLine1(req.addressLine1())
                        .city(req.city())
                        .postalCode(req.postalCode())
                        .country(req.country())
                        .isDefault(req.isDefault())
                        .build();
        addressRepo.save(addr);
        return userMapper.toAddressResponse(addr);
    }

    public AddressResponse updateAddress(UUID addressId, AddressRequest req) {
        UserAddress addr = loadAddressAndVerifyOwnership(addressId);
        addr.update(
                req.label(),
                req.addressLine1(),
                req.city(),
                req.postalCode(),
                req.country(),
                req.isDefault());
        addressRepo.save(addr);
        return userMapper.toAddressResponse(addr);
    }

    public void deleteAddress(UUID addressId) {
        UserAddress addr = loadAddressAndVerifyOwnership(addressId);
        addressRepo.delete(addr);
    }

    /**
     * Edge Case #16 — Address Ownership Check: Verifies the address belongs to the currently
     * authenticated user. Prevents users from reading/modifying/using other users' addresses. Used in
     * checkout to validate the shipping address.
     */
    public UserAddress loadAddressAndVerifyOwnership(UUID addressId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserAddress addr =
                addressRepo
                        .findById(addressId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));

        if (!addr.getUser().getId().equals(currentUserId)) {
            throw new ResourceOwnershipException("Address", addressId);
        }
        return addr;
    }


}
