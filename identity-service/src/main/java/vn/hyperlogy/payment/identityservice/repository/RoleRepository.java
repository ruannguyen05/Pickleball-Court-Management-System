package vn.hyperlogy.payment.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.hyperlogy.payment.identityservice.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {}
