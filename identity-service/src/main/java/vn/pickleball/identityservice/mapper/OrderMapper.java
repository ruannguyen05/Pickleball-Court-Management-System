package vn.pickleball.identityservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.pickleball.identityservice.dto.BookingStatus;
import vn.pickleball.identityservice.dto.request.OrderDetailRequest;
import vn.pickleball.identityservice.dto.request.OrderRequest;
import vn.pickleball.identityservice.dto.request.PermissionRequest;
import vn.pickleball.identityservice.dto.request.UpdateBookingSlot;
import vn.pickleball.identityservice.dto.response.OrderDetailResponse;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.dto.response.PermissionResponse;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.OrderDetail;
import vn.pickleball.identityservice.entity.Permission;

import java.time.LocalTime;
import java.util.*;

@Mapper(componentModel = "spring" , imports = BookingStatus.class)
public interface OrderMapper {
    @Mapping(target = "paymentStatus", constant = "Chưa thanh toán")
    @Mapping(target = "orderStatus", constant = "Đang xử lý")
    Order toEntity(OrderRequest request);

    @Mapping(source = "order.id", target = "id")
    OrderResponse toResponse(Order order);

    List<OrderDetail> toEntity(List<OrderDetailRequest> orderDetails);

    List<OrderDetailResponse> toResponse(List<OrderDetail> orderDetails);

    OrderDetail toEntity(OrderDetailRequest request);

    OrderDetailResponse toResponse(OrderDetail orderDetail);

    List<OrderResponse> toResponseList(List<Order> orders);


    @Mapping(target = "courtId", source = "courtId")
    @Mapping(target = "dateBooking", source = "bookingDate")
    @Mapping(target = "status", expression = "java(BookingStatus.BOOKED)")
    @Mapping(target = "courtSlotBookings", expression = "java(mapCourtSlotBookings(orderRequest.getOrderDetails()))")
    UpdateBookingSlot toUpdateBookingSlot(OrderRequest orderRequest);

//    @Mapping(target = "courtId", ignore = true)
//    @Mapping(target = "dateBooking", ignore = true)
//    @Mapping(target = "status", expression = "java(BookingStatus.BOOKED)")
//    @Mapping(target = "courtSlotBookings", expression = "java(mapCourtSlotBookingsV2(order.getOrderDetails()))")
//    UpdateBookingSlot orderToUpdateBookingSlot(Order order);


    default Map<String, List<LocalTime>> mapCourtSlotBookings(List<OrderDetailRequest> orderDetails) {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<LocalTime>> courtSlotBookings = new HashMap<>();

        for (OrderDetailRequest detail : orderDetails) {
            List<LocalTime> timeSlots = splitTimeSlots(detail.getStartTime(), detail.getEndTime());
            courtSlotBookings.put(detail.getCourtSlotName(), timeSlots);
        }

        return courtSlotBookings;
    }


//    @Named("mapCourtSlotBookingsV2")
//    default Map<String, List<LocalTime>> mapCourtSlotBookingsV2(List<OrderDetail> orderDetails) {
//        if (orderDetails == null || orderDetails.isEmpty()) {
//            return Collections.emptyMap();
//        }
//        Map<String, List<LocalTime>> courtSlotBookings = new HashMap<>();
//        for (OrderDetail detail : orderDetails) {
//            List<LocalTime> timeSlots = splitTimeSlots(detail.getStartTime(), detail.getEndTime());
//            courtSlotBookings.put(detail.getCourtSlotName(), timeSlots);
//        }
//        return courtSlotBookings;
//    }


    default List<LocalTime> splitTimeSlots(LocalTime startTime, LocalTime endTime) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = startTime;

        while (current.isBefore(endTime)) {
            slots.add(current);
            current = current.plusMinutes(30);
        }

        return slots;
    }
}
