package vn.hyperlogy.payment.identityservice.mapper;

import org.mapstruct.Mapper;
import vn.hyperlogy.payment.identityservice.dto.request.PermissionRequest;
import vn.hyperlogy.payment.identityservice.dto.response.PermissionResponse;
import vn.hyperlogy.payment.identityservice.entity.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}
