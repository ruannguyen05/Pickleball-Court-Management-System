package vn.pickleball.identityservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByPhoneNumber(String phoneNumber);

    @Query("SELECT n FROM Order n WHERE n.user.id = :value OR n.phoneNumber = :value ORDER BY n.createdAt DESC")
    List<Order> findByPhoneNumberOrUserId(String value);

    List<Order> findByBookingDateAndCourtId(LocalDate bookingDate, String courtId);

//    List<Order> findByOrderStatusInAndCourtIdAndBookingDate(
//            List<String> orderStatuses, String courtId, LocalDate bookingDate
//    );

    @Query("SELECT o FROM Order o " +
            "JOIN o.orderDetails od " +
            "JOIN od.bookingDates bd " +
            "WHERE o.courtId = :courtId " +
            "AND o.orderStatus IN :orderStatuses " +
            "AND bd.bookingDate = :bookingDate")
    List<Order> findByOrderStatusInAndCourtIdAndBookingDate(
            @Param("orderStatuses") List<String> orderStatuses,
            @Param("courtId") String courtId,
            @Param("bookingDate") LocalDate bookingDate
    );

//    List<Order> findByOrderStatusNotAndPaymentStatusAndSettlementTimeLessThanEqual(
//            String orderStatus, String paymentStatus, LocalDateTime settlementTime
//    );

    List<Order> findByPaymentStatus(String status);

    @Query("SELECT o FROM Order o " +
            "WHERE (:courtId IS NULL OR o.courtId = :courtId) " +
            "AND (:orderStatus IS NULL OR o.orderStatus = :orderStatus) " +
            "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
            "AND (:startDate IS NULL OR EXISTS (SELECT 1 FROM OrderDetail od JOIN od.bookingDates bd WHERE od.order = o AND bd.bookingDate >= :startDate)) " +
            "AND (:endDate IS NULL OR EXISTS (SELECT 1 FROM OrderDetail od JOIN od.bookingDates bd WHERE od.order = o AND bd.bookingDate <= :endDate)) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findOrders(
            @Param("courtId") String courtId,
            @Param("orderStatus") String orderStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );



    @Query("SELECT DISTINCT od.courtSlotName, bd.bookingDate FROM OrderDetail od " +
            "JOIN od.bookingDates bd " +
            "JOIN od.order o " +
            "WHERE o.courtId = :courtId " +
            "AND o.orderStatus IN :orderStatuses " +
            "AND bd.bookingDate IN :bookingDates " +
            "AND (od.startTime < :endTime AND od.endTime > :startTime)")
    List<Object[]> findBookedCourtSlots(@Param("courtId") String courtId,
                                        @Param("bookingDates") List<LocalDate> bookingDates,
                                        @Param("startTime") LocalTime startTime,
                                        @Param("endTime") LocalTime endTime,
                                        @Param("orderStatuses") List<String> orderStatuses);


}
