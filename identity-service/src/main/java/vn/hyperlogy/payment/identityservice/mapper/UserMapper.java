package vn.hyperlogy.payment.identityservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.hyperlogy.payment.identityservice.dto.request.UserCreationRequest;
import vn.hyperlogy.payment.identityservice.dto.request.UserUpdateRequest;
import vn.hyperlogy.payment.identityservice.dto.response.UserResponse;
import vn.hyperlogy.payment.identityservice.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "roles", ignore = true)
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
