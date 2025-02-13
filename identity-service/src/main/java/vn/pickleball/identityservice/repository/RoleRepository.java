package vn.pickleball.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {}
