package vn.pickleball.identityservice.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.pickleball.identityservice.dto.request.RoleRequest;
import vn.pickleball.identityservice.dto.response.RoleResponse;
import vn.pickleball.identityservice.entity.Role;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.mapper.RoleMapper;
import vn.pickleball.identityservice.repository.RoleRepository;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleService {
    RoleRepository roleRepository;
    RoleMapper roleMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public RoleResponse create(RoleRequest request) {
        var role = roleMapper.toRole(request);

        role = roleRepository.save(role);
        return roleMapper.toRoleResponse(role);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Role> getRolesWithoutAdmin() {
        return roleRepository.findRolesWithoutAdmin("ADMIN");
    }

    public RoleResponse updateRole(RoleRequest request) {
        Role role = roleRepository.findById(request.getName())
                .orElseThrow(() -> new ApiException("Role not found", "ENTITY_NOTFOUND"));


        role.setDescription(request.getDescription());
        roleRepository.save(role);
        return roleMapper.toRoleResponse(role);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoleResponse> getAll() {
        return roleRepository.findRolesWithoutAdmin("ADMIN").stream().map(roleMapper::toRoleResponse).toList();
    }

    public void delete(String role) {
        roleRepository.deleteById(role);
    }
}
