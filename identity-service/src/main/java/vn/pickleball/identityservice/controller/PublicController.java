package vn.pickleball.identityservice.controller;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.identityservice.dto.payment.MbVietQrRefundWithAmountRequest;
import vn.pickleball.identityservice.dto.request.FCMTokenRequest;
import vn.pickleball.identityservice.dto.request.NotificationRequest;
import vn.pickleball.identityservice.dto.request.OrderRequest;
import vn.pickleball.identityservice.dto.request.UpdateBookingSlot;
import vn.pickleball.identityservice.dto.response.NotificationResponse;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.dto.response.PaymentData;
import vn.pickleball.identityservice.service.EmailService;
import vn.pickleball.identityservice.service.FCMService;
import vn.pickleball.identityservice.service.NotificationService;
import vn.pickleball.identityservice.service.OrderService;
import vn.pickleball.identityservice.utils.GenerateString;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {
    private final OrderService orderService;
    private final FCMService fcmService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @PostMapping("/create_order")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/change_order")
    public ResponseEntity<OrderResponse> changeOrder(@RequestParam String orderId,@Valid @RequestBody OrderRequest orderRequest) {
        OrderResponse orderResponse = orderService.changeOrder(orderRequest, orderId);
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/paymentNotify")
    public ResponseEntity<String> receivePaymentNotification(@RequestBody vn.pickleball.identityservice.dto.payment.NotificationResponse response) {
        try {
            orderService.paymentNotification(response);
            return ResponseEntity.ok("Notification received successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to process notification");
        }
    }

    @PostMapping("/refund")
    public ResponseEntity<String> receivePaymentNotification(@RequestBody MbVietQrRefundWithAmountRequest refundWithAmountRequest) {
            orderService.refund(refundWithAmountRequest);
            return ResponseEntity.ok("REFUND_SUCCESS");
    }

    @GetMapping("/getOrders")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam String value) {

        List<OrderResponse> orders = orderService.getOrders(value);
        return ResponseEntity.ok(orders);
    }
    @GetMapping("/getOrderById")
    public ResponseEntity<OrderResponse> getOrderById(
            @RequestParam String orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    @PutMapping("/cancelOrder")
    public ResponseEntity<OrderResponse> cancelOrder(
            @RequestParam String orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }

    @PostMapping("/notification/save-token")
    public ResponseEntity<?> saveToken(@RequestBody FCMTokenRequest request) {
        fcmService.saveFcmToken(request.getKey(), request.getToken());
        return ResponseEntity.ok("FCM Token saved successfully");
    }

    @PutMapping("/notification/read")
    public ResponseEntity<NotificationRequest> updateStatus(
            @RequestParam String id) {
        return ResponseEntity.ok(notificationService.updateStatus(id));
    }


    @GetMapping("/notification/getNotifications")
    public ResponseEntity<NotificationResponse> getAllNotifications(@RequestParam String value) {
        return ResponseEntity.ok(notificationService.getAllNotifications(value));
    }

    @GetMapping("/notification/courtUnRead")
    public ResponseEntity<Long> courtUnRead(@RequestParam String value) {
        return ResponseEntity.ok(notificationService.courtUnRead(value));
    }

    @PostMapping("/sendEmail")
    public String sendEmal(@RequestBody OrderResponse response){
        emailService.sendBookingConfirmationEmail("ruannvhe160301@fpt.edu.vn",response);
        return "success";
    }

    @PostMapping("/test_send-fcm")
    public void testFCM(@RequestParam String key){
        orderService.testFcm(key);
    }

    @GetMapping("/booked-slots")
    public ResponseEntity<UpdateBookingSlot> getBookedSlots(
            @RequestParam String courtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bookingDate) {

        UpdateBookingSlot bookedSlots = orderService.getBookedSlot(courtId, bookingDate);

        return ResponseEntity.ok(bookedSlots);
    }
    @GetMapping("/getSignature")
    public String getSignature(@RequestParam String totalAmount,@RequestParam String paymentAmount,
                               @RequestParam String depositAmount,@RequestParam String bookingDate){
        return GenerateString.encode(totalAmount,paymentAmount,depositAmount, bookingDate);
    }

}
