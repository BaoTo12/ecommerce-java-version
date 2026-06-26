package com.ecommerce.domain.user.domain.ports.in;

import com.ecommerce.domain.user.domain.model.UserAddress;
import com.ecommerce.domain.user.dto.AddressRequest;
import com.ecommerce.domain.user.dto.AddressResponse;
import com.ecommerce.domain.user.dto.UpdateProfileRequest;
import com.ecommerce.domain.user.dto.UserProfileResponse;

import java.util.List;
import java.util.UUID;

public interface UserUseCase {
    UserProfileResponse getProfile();
    UserProfileResponse updateProfile(UpdateProfileRequest req);
    void deleteAccount(UUID userId);
    List<AddressResponse> getAddresses();
    AddressResponse addAddress(AddressRequest req);
    AddressResponse updateAddress(UUID addressId, AddressRequest req);
    void deleteAddress(UUID addressId);
    UserAddress loadAddressAndVerifyOwnership(UUID addressId);
}
