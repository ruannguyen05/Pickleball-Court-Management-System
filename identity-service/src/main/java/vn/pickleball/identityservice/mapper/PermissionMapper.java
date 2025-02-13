package vn.pickleball.identityservice.mapper;

import org.mapstruct.Mapper;
import vn.pickleball.identityservice.dto.request.PermissionRequest;
import vn.pickleball.identityservice.dto.response.PermissionResponse;
import vn.pickleball.identityservice.entity.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}
