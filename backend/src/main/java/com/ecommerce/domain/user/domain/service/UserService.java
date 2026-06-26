package com.ecommerce.domain.user.domain.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ResourceOwnershipException;
import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.domain.user.domain.model.User;
import com.ecommerce.domain.user.domain.model.UserAddress;
import com.ecommerce.domain.user.domain.ports.in.UserUseCase;
import com.ecommerce.domain.user.domain.ports.out.UserAddressRepositoryPort;
import com.ecommerce.domain.user.domain.ports.out.UserRepositoryPort;
import com.ecommerce.domain.user.dto.AddressRequest;
import com.ecommerce.domain.user.dto.AddressResponse;
import com.ecommerce.domain.user.dto.UpdateProfileRequest;
import com.ecommerce.domain.user.dto.UserProfileResponse;
import com.ecommerce.domain.user.mapper.UserMapper;

import java.util.List;
import java.util.UUID;

public class UserService implements UserUseCase {

    private final UserRepositoryPort userRepo;
    private final UserAddressRepositoryPort addressRepo;
    private final UserMapper userMapper;

    public UserService(UserRepositoryPort userRepo, UserAddressRepositoryPort addressRepo, UserMapper userMapper) {
        this.userRepo = userRepo;
        this.addressRepo = addressRepo;
        this.userMapper = userMapper;
    }

    @Override
    public UserProfileResponse getProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        return userMapper.toProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.updateProfile(req.name(), req.phone());
        userRepo.save(user);
        return userMapper.toProfileResponse(user);
    }

    @Override
    public void deleteAccount(UUID userId) {
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.softDelete();
        userRepo.save(user);
    }

    @Override
    public List<AddressResponse> getAddresses() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return addressRepo.findByUserId(userId).stream()
                .map(userMapper::toAddressResponse)
                .toList();
    }

    @Override
    public AddressResponse addAddress(AddressRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepo
                .findActiveById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        UserAddress addr = UserAddress.builder()
                .userId(user.getId())
                .label(req.label())
                .addressLine1(req.addressLine1())
                .city(req.city())
                .postalCode(req.postalCode())
                .country(req.country() != null ? req.country() : "Vietnam")
                .isDefault(req.isDefault())
                .build();
        addressRepo.save(addr);
        return userMapper.toAddressResponse(addr);
    }

    @Override
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

    @Override
    public void deleteAddress(UUID addressId) {
        UserAddress addr = loadAddressAndVerifyOwnership(addressId);
        addressRepo.delete(addr);
    }

    @Override
    public UserAddress loadAddressAndVerifyOwnership(UUID addressId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserAddress addr = addressRepo
                .findById(addressId)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));

        if (!addr.getUserId().equals(currentUserId)) {
            throw new ResourceOwnershipException("Address", addressId);
        }
        return addr;
    }
}
