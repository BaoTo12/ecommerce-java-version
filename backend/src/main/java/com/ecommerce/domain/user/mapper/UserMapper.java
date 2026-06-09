package com.ecommerce.domain.user.mapper;

import com.ecommerce.domain.user.dto.AddressResponse;
import com.ecommerce.domain.user.dto.UserProfileResponse;
import com.ecommerce.domain.user.entity.User;
import com.ecommerce.domain.user.entity.UserAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserProfileResponse toProfileResponse(User user);

    @Mapping(target = "isDefault", source = "default")
    AddressResponse toAddressResponse(UserAddress address);
}
