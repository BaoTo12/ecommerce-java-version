package com.ecommerce.monolith.domain.user.mapper;

import com.ecommerce.monolith.domain.user.dto.AddressResponse;
import com.ecommerce.monolith.domain.user.dto.UserProfileResponse;
import com.ecommerce.monolith.domain.user.entity.User;
import com.ecommerce.monolith.domain.user.entity.UserAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserProfileResponse toProfileResponse(User user);

    @Mapping(target = "isDefault", source = "default")
    AddressResponse toAddressResponse(UserAddress address);
}
