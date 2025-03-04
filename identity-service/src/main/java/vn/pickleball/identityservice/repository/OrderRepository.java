package vn.pickleball.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.Role;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByPhoneNumber(String phoneNumber);

    List<Order> findByPhoneNumberOrUserId(String phoneNumber, String userId);
}
