package vn.pickleball.identityservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.identityservice.client.CourtClient;
import vn.pickleball.identityservice.client.PaymentClient;
import vn.pickleball.identityservice.dto.BookingStatus;
import vn.pickleball.identityservice.dto.notification.NotificationResponse;
import vn.pickleball.identityservice.dto.request.CreateQrRequest;
import vn.pickleball.identityservice.dto.request.OrderRequest;
import vn.pickleball.identityservice.dto.request.TransactionRequest;
import vn.pickleball.identityservice.dto.request.UpdateBookingSlot;
import vn.pickleball.identityservice.dto.response.MbQrCodeResponse;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.dto.response.PaymentData;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.OrderDetail;
import vn.pickleball.identityservice.entity.Transaction;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.mapper.OrderMapper;
import vn.pickleball.identityservice.mapper.TransactionMapper;
import vn.pickleball.identityservice.repository.OrderRepository;
import vn.pickleball.identityservice.repository.TransactionRepository;
import vn.pickleball.identityservice.utils.GenerateString;
import vn.pickleball.identityservice.websockethandler.NotificationWebSocketHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final OrderMapper orderMapper;
    private final PaymentClient paymentClient;
    private final CourtClient courtClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final ScheduledExecutorService scheduler;
    private final NotificationWebSocketHandler socketHandler;
    private final TransactionMapper transactionMapper;

    private static final String TRANSACTION_KEY_PREFIX = "transaction:";
    private static final String PREFIX = "bookingslot:";

    @Value("${payment.api_key}")
    private String apiKey;

    @Value("${payment.client_id}")
    private String clientId;

    @Value("${payment.check_sum}")
    private String checkSum;

    public OrderResponse createOrder(OrderRequest request) {
        Order order = orderMapper.toEntity(request);
        List<OrderDetail> orderDetails = orderMapper.toEntity(request.getOrderDetails());

        orderDetails.forEach(detail -> detail.setOrder(order));
        order.setPaymentTimeout(LocalDateTime.now().plusMinutes(5));
        order.setOrderDetails(orderDetails);
        order.setTotalAmount(request.getTotalAmount());
        order.setPaymentAmount(request.getPaymentAmount());
        order.setOrderType("Đơn ngày");
        order.setPaymentStatus(request.getPaymentStatus());

        Double amount = request.getPaymentAmount().doubleValue();
        String billCode = GenerateString.generateRandomString(15);

        // Tạo QR thanh toán
        CreateQrRequest qrRequest = CreateQrRequest.builder()
                .billCode(billCode)
                .amount(amount)
                .signature(encrypt(amount, billCode, checkSum))
                .build();
        saveBookingSlot(orderMapper.toUpdateBookingSlot(request), billCode);
        log.info("send update booking status");
        try {
            courtClient.updateBookingSlots(orderMapper.toUpdateBookingSlot(request));
        } catch (FeignException e) {
            throw new ApiException("Failed to update booking slot", "API_UPDATE_BOOKING_SLOT_ERROR");
        }

        MbQrCodeResponse qrCodeResponse;
        try {
            qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
        } catch (FeignException e) {
            throw new ApiException("Failed to create QR", "API_MB_CREATE_QR_ERROR");
        }

        Order savedOrder = orderRepository.save(order);

        TransactionRequest transaction = TransactionRequest.builder()
                .orderId(savedOrder.getId())
                .amount(request.getAmountPaid())
                .paymentStatus(savedOrder.getPaymentStatus())
                .billCode(billCode)
                .build();

        saveTransactionToRedis(transaction);


        // Lên lịch kiểm tra sau 5 phút
        schedulePaymentCheck(billCode);

        OrderResponse response = orderMapper.toResponse(savedOrder);
        response.setQrcode(qrCodeResponse.getQrCode());
        return response;
    }

    /**
     * Lên lịch kiểm tra thanh toán sau 5 phút
     */
    private void schedulePaymentCheck(String billCode) {
        scheduler.schedule(() -> processPaymentTimeout(billCode), 5, TimeUnit.MINUTES);
    }

    /**
     * Xử lý đơn hàng quá hạn thanh toán
     */
    private void processPaymentTimeout(String billCode) {
        String key = TRANSACTION_KEY_PREFIX + billCode;
        if (!redisTemplate.hasKey(key)) {
            log.info("Payment received, no action needed for billCode: {}", billCode);
            return;
        }

        TransactionRequest transactionRequest = getTransactionRedis(billCode);

        // Cập nhật trạng thái đơn hàng
        Order order = orderRepository.findById(transactionRequest.getOrderId())
                .orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));
        log.info("send websocket");
        socketHandler.sendNotification(NotificationResponse.builder()
                .key(order.getId())
                .resDesc("Not payment")
                .resCode("400")
                .build());

        order.setOrderStatus("Hủy do quá giờ thanh toán");
        order.setPaymentStatus("Chưa thanh toán");
        orderRepository.save(order);
        log.info("Update order status");
        // Cập nhật trạng thái sân
        UpdateBookingSlot updateBookingSlot = getBookingSlot(billCode);
        updateBookingSlot.setStatus(BookingStatus.AVAILABLE);
        log.info("Update booking body: {}", updateBookingSlot);
        try {
            courtClient.updateBookingSlots(updateBookingSlot);
        } catch (FeignException e) {
            log.error("Failed to update booking slot", e);
            throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
        }

        log.info("Order {} has been cancelled due to payment timeout.", order.getId());
    }

    public void paymentNotification(PaymentData paymentData){
        TransactionRequest transactionRequest = getTransactionRedis(paymentData.getBillCode());
        Order order = orderRepository.findById(transactionRequest.getOrderId()).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));
        order.setOrderStatus("Đặt lịch thành công");
        String paymentStatus = order.getPaymentStatus().equals("Đặt cọc") ? "Đã đặt cọc" : "Đã thanh toán";
        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
        socketHandler.sendNotification(NotificationResponse.builder()
                        .key(order.getId())
                        .resDesc("Payment successfully")
                        .resCode("200")
                .build());
        Transaction transaction = Transaction.builder()
                .order(order)
                .amount(transactionRequest.getAmount())
                .paymentStatus(paymentStatus)
                .billCode(paymentData.getBillCode())
                .ftCode(paymentData.getFtCode())
                .createDate(LocalDateTime.now())
                .status("Success")
                .build();
        transactionRepository.save(transaction);
        deleteTransactionRedis(paymentData.getBillCode());
    }


    public static String encrypt(Double amount, String billCode, String checksumKey) {
        try {
            String input = amount + "|" + billCode;

            SecretKeySpec secretKeySpec = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting with HMAC");
        }
    }

    public void saveTransactionToRedis(TransactionRequest transaction) {
        try {
            String key = TRANSACTION_KEY_PREFIX + transaction.getBillCode();
            String value = redisObjectMapper.writeValueAsString(transaction);
            redisTemplate.opsForValue().set(key, value, 6, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction", e);
        }
    }

    public TransactionRequest getTransactionRedis(String billcode) {
        try {
            String key = TRANSACTION_KEY_PREFIX + billcode;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return redisObjectMapper.readValue(value, TransactionRequest.class);
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize transaction", e);
        }
    }

    public void deleteTransactionRedis(String billcode) {
        String key = TRANSACTION_KEY_PREFIX + billcode;
        redisTemplate.delete(key);
    }

    public void saveBookingSlot(UpdateBookingSlot bookingSlot, String billCode) {
        try {
            String key = PREFIX + billCode;
            String value = redisObjectMapper.writeValueAsString(bookingSlot);
            redisTemplate.opsForValue().set(key, value, 6, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize booking slot", e);
        }
    }

    // Get Booking Slot from Redis
    public UpdateBookingSlot getBookingSlot(String billCode) {
        try {
            String key = PREFIX + billCode;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return redisObjectMapper.readValue(value, UpdateBookingSlot.class);
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize booking slot", e);
        }
    }

    public List<OrderResponse> getOrders(String userId, String phoneNumber) {
        List<Order> orders;

        if (userId != null) {
            orders = orderRepository.findByPhoneNumberOrUserId(phoneNumber, userId);
        } else {
            orders = orderRepository.findByPhoneNumber(phoneNumber);
        }

        return orderMapper.toResponseList(orders);
    }
}

