package vn.pickleball.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username = :value OR u.email = :value OR u.phoneNumber = :value")
    Optional<User> findByUsernameOrEmailOrPhoneNumber(String value);
}
