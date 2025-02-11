package vn.hyperlogy.payment.identityservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.hyperlogy.payment.identityservice.dto.request.RoleRequest;
import vn.hyperlogy.payment.identityservice.dto.response.RoleResponse;
import vn.hyperlogy.payment.identityservice.entity.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @Mapping(target = "permissions", ignore = true)
    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);
}
