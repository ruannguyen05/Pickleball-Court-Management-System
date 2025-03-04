package vn.pickleball.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.OrderDetail;
import vn.pickleball.identityservice.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {}
