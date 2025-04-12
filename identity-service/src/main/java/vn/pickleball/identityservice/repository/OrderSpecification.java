package vn.pickleball.identityservice.repository;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import vn.pickleball.identityservice.entity.BookingDate;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.OrderDetail;

import java.time.LocalDate;
import java.util.List;

public class OrderSpecifications {

    public static Specification<Order> hasCourtId(String courtId) {
        return (root, query, cb) ->
                courtId != null ? cb.equal(root.get("courtId"), courtId) : null;
    }

    public static Specification<Order> hasOrderStatus(String orderStatus) {
        return (root, query, cb) ->
                orderStatus != null ? cb.equal(root.get("orderStatus"), orderStatus) : null;
    }

    public static Specification<Order> hasPaymentStatus(String paymentStatus) {
        return (root, query, cb) ->
                paymentStatus != null ? cb.equal(root.get("paymentStatus"), paymentStatus) : null;
    }

    public static Specification<Order> hasBookingDateBetween(LocalDate start, LocalDate end) {
        if (start == null && end == null) return null;

        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<OrderDetail> orderDetailRoot = subquery.from(OrderDetail.class);
            Join<OrderDetail, BookingDate> bookingDateJoin = orderDetailRoot.join("bookingDates");

            Predicate datePredicate = null;
            if (start != null && end != null) {
                datePredicate = cb.between(bookingDateJoin.get("bookingDate"), start, end);
            } else if (start != null) {
                datePredicate = cb.greaterThanOrEqualTo(bookingDateJoin.get("bookingDate"), start);
            } else {
                datePredicate = cb.lessThanOrEqualTo(bookingDateJoin.get("bookingDate"), end);
            }

            subquery.select(cb.literal(1L))
                    .where(
                            cb.equal(orderDetailRoot.get("order"), root),
                            datePredicate
                    );

            return cb.exists(subquery);
        };
    }
}
