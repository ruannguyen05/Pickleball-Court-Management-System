package vn.pickleball.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.identityservice.dto.request.OrderRequest;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.dto.response.PaymentData;
import vn.pickleball.identityservice.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {
    private final OrderService orderService;

    @PostMapping("/create_order")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest) {
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/paymentNotify")
    public ResponseEntity<String> receivePaymentNotification(@RequestBody PaymentData paymentData) {
        try {
            orderService.paymentNotification(paymentData);
            return ResponseEntity.ok("Notification received successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to process notification");
        }
    }

    @GetMapping("/getOrders")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam String phoneNumber,
            @RequestParam(required = false) String userId) {

        List<OrderResponse> orders = orderService.getOrders(userId, phoneNumber);
        return ResponseEntity.ok(orders);
    }
}
