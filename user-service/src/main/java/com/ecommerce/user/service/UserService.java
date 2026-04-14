package com.ecommerce.user.service;

import com.ecommerce.shared.security.SecurityUtils;
import com.ecommerce.user.model.dto.*;
import com.ecommerce.user.model.entity.UserAddressEntity;
import com.ecommerce.user.model.entity.UserEntity;
import com.ecommerce.user.repository.UserAddressRepository;
import com.ecommerce.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

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
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getName(),
                user.getPhone(), user.getCreatedAt());
    }

    public UserProfileResponse updateProfile(UpdateProfileRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.updateProfile(req.name(), req.phone());
        userRepo.save(user);
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getName(),
                user.getPhone(), user.getCreatedAt());
    }

    // --- Address CRUD ---

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return addressRepo.findByUserId(userId).stream()
                .map(this::toAddressResponse)
                .toList();
    }

    public AddressResponse addAddress(AddressRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        UserAddressEntity addr = UserAddressEntity.create(user, req.label(), req.addressLine1(),
                req.addressLine2(), req.city(), req.state(), req.postalCode(),
                req.country() != null ? req.country() : "Vietnam", req.isDefault());
        addressRepo.save(addr);
        return toAddressResponse(addr);
    }

    public AddressResponse updateAddress(UUID addressId, AddressRequest req) {
        UserAddressEntity addr = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found: " + addressId));

        UUID userId = SecurityUtils.getCurrentUserId();
        if (!addr.getUser().getId().equals(userId)) {
            throw new RuntimeException("Address does not belong to current user");
        }

        addr.update(req.label(), req.addressLine1(), req.addressLine2(), req.city(),
                req.state(), req.postalCode(),
                req.country() != null ? req.country() : "Vietnam", req.isDefault());
        addressRepo.save(addr);
        return toAddressResponse(addr);
    }

    public void deleteAddress(UUID addressId) {
        UserAddressEntity addr = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found: " + addressId));
        UUID userId = SecurityUtils.getCurrentUserId();
        if (!addr.getUser().getId().equals(userId)) {
            throw new RuntimeException("Address does not belong to current user");
        }
        addressRepo.delete(addr);
    }

    private AddressResponse toAddressResponse(UserAddressEntity a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getAddressLine1(), a.getAddressLine2(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.isDefault());
    }
}
