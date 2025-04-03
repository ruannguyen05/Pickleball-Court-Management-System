package vn.pickleball.identityservice.mapper;

import org.mapstruct.*;
import vn.pickleball.identityservice.dto.request.UserCreationRequest;
import vn.pickleball.identityservice.dto.request.UserUpdateRequest;
import vn.pickleball.identityservice.dto.response.UserResponse;
import vn.pickleball.identityservice.entity.User;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class UserMapper {
    @Mapping(target = "roles", ignore = true)
    public abstract User toUser(UserCreationRequest request);

    @Mapping(source = "student", target = "student")
    public abstract UserResponse toUserResponse(User user);

    public abstract List<UserResponse> toUsersResponses(List<User> users);

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "active" , ignore = true)
    public abstract void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
