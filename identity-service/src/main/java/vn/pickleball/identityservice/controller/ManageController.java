package vn.pickleball.identityservice.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.identityservice.dto.response.OrderPage;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.service.OrderService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/manage")
@RequiredArgsConstructor
public class ManageController {
    private final OrderService orderService;

    @GetMapping("/order/by-booking-date")
    public ResponseEntity<List<OrderResponse>> getOrdersByBookingDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookingDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime endTime) {

        List<OrderResponse> responses = orderService.getOrdersByBookingDateAndTimeRange(
                bookingDate,
                startTime,
                endTime);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/order/by-booking-date-phone")
    public ResponseEntity<List<OrderResponse>> getOrdersByBookingDatePhoneAndStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookingDate,
            @RequestParam String phoneNumber) {

        List<OrderResponse> orders = orderService.getOrdersByBookingDateAndPhoneNumberWithStatus(
                bookingDate,
                phoneNumber);

        return ResponseEntity.ok(orders);
    }

    @GetMapping("/getByStaff")
    public ResponseEntity<OrderPage> getOrdersByFilter(
            @RequestParam(required = false) LocalDate bookingDate,
            @RequestParam(required = false) LocalTime startTime,
            @RequestParam(required = false) LocalTime endTime,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        OrderPage orders = orderService.getOrdersByFilterByStaff(
                bookingDate,
                startTime,
                endTime,
                phoneNumber,
                customerName,
                statuses,
                page,
                size
        );
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/order/checkin")
    public ResponseEntity<OrderResponse> checkin(
            @RequestParam String orderId) {


        return ResponseEntity.ok(orderService.checkin(orderId));
    }


}
