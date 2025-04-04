package vn.pickleball.identityservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.OrderDetail;
import vn.pickleball.identityservice.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    @Query("SELECT t FROM Transaction t WHERE (:paymentStatus IS NULL OR t.paymentStatus = :paymentStatus) " +
            "AND (:courtId IS NULL OR t.courtId = :courtId) " +
            "AND (:orderId IS NULL OR t.order.id = :orderId) " +
            "AND (:startDate IS NULL OR t.createDate >= :startDate) " +
            "AND (:endDate IS NULL OR t.createDate <= :endDate) " +
            "ORDER BY t.createDate DESC")
    Page<Transaction> findTransactions(@Param("paymentStatus") String paymentStatus,
                                       @Param("courtId") String courtId,
                                       @Param("orderId") String orderId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       Pageable pageable);


    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.paymentStatus <> 'Hoàn tiền' " +
            "AND (:courtId IS NULL OR t.courtId = :courtId) " +
            "AND (:startDate IS NULL OR t.createDate >= :startDate) " +
            "AND (:endDate IS NULL OR t.createDate <= :endDate)")
    BigDecimal getTotalAmountExcludingRefund(@Param("courtId") String courtId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.paymentStatus = 'Hoàn tiền' " +
            "AND (:courtId IS NULL OR t.courtId = :courtId) " +
            "AND (:startDate IS NULL OR t.createDate >= :startDate) " +
            "AND (:endDate IS NULL OR t.createDate <= :endDate)")
    BigDecimal getTotalRefundAmount(@Param("courtId") String courtId,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE t.order.id = :orderId ORDER BY t.createDate DESC")
    List<Transaction> findByOrderId(@Param("orderId") String orderId);


}
