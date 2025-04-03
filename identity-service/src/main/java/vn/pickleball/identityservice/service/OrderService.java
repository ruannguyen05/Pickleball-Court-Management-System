package vn.pickleball.identityservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.identityservice.client.CourtClient;
import vn.pickleball.identityservice.client.PaymentClient;
import vn.pickleball.identityservice.dto.BookingStatus;
//import vn.pickleball.identityservice.dto.notification.NotificationResponse;
import vn.pickleball.identityservice.dto.payment.CreateQrRequest;
import vn.pickleball.identityservice.dto.payment.MbVietQrRefundWithAmountRequest;
import vn.pickleball.identityservice.dto.payment.NotificationResponse;
import vn.pickleball.identityservice.dto.request.*;
import vn.pickleball.identityservice.dto.response.FixedBookingResponse;
import vn.pickleball.identityservice.dto.response.FixedResponse;
import vn.pickleball.identityservice.dto.response.MbQrCodeResponse;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.entity.*;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.mapper.OrderMapper;
import vn.pickleball.identityservice.mapper.TransactionMapper;
import vn.pickleball.identityservice.repository.*;
import vn.pickleball.identityservice.utils.GenerateString;
import vn.pickleball.identityservice.websockethandler.NotificationWebSocketHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final FCMService fcmService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OrderDetailRepository orderDetailRepository;
    private final BookingDateRepository bookingDateRepository;

    private static final String TRANSACTION_KEY_PREFIX = "transaction:";
    private static final String PREFIX = "bookingslot:";
    private static final String PREFIX_QR = "qrcode:";

    @Value("${payment.api_key}")
    private String apiKey;

    @Value("${payment.client_id}")
    private String clientId;

    @Value("${payment.check_sum}")
    private String checkSum;

    public OrderResponse createOrder(OrderRequest request) {
        if(!GenerateString.isValidSignature(request.getTotalAmount().toString(),
                request.getPaymentAmount().toString(),
                request.getDepositAmount().toString(),
                request.getPhoneNumber(),
                request.getSignature())){
            throw new ApiException("Payment amount not match", "AMOUNT_NOT_MATCH");
        }

        Order order = orderMapper.toOrder(request);

        order.setPaymentTimeout(LocalDateTime.now().plusMinutes(5));
        order.setTotalAmount(request.getTotalAmount());
        order.setPaymentAmount(request.getPaymentAmount());
        order.setDepositAmount(request.getDepositAmount());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : "Đơn ngày");
        order.setPaymentStatus(request.getPaymentStatus() != null ? request.getPaymentStatus() : "Chưa đặt cọc");
        if(request.getUserId() != null && !request.getUserId().isEmpty()){
            order.setUser(userRepository.findById(request.getUserId()).orElseThrow(() -> null));
        }
//        LocalTime latestEndTime = Collections.max(request.getOrderDetails(),
//                        Comparator.comparing(OrderDetailRequest::getEndTime))
//                .getEndTime();
//        order.setSettlementTime(LocalDateTime.of(request.getBookingDate(),latestEndTime.plusHours(1)));

        Double amount = request.getPaymentAmount().doubleValue();
        String billCode = "PM" + GenerateString.generateRandomString(13);

        order.setBillCode(billCode);
        List<UpdateBookingSlot> updateBookingSlots = orderMapper.toUpdateBookingSlotList(request);
        // Tạo QR thanh toán
        CreateQrRequest qrRequest = CreateQrRequest.builder()
                .billCode(billCode)
                .amount(amount)
                .signature(encrypt(amount, billCode, checkSum))
                .build();
        saveBookingSlot(updateBookingSlots, billCode);
        MbQrCodeResponse qrCodeResponse;
        try {
            qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
        } catch (FeignException e) {
            throw new ApiException(e.getMessage(), "API_MB_CREATE_QR_ERROR");
        }
        log.info("send update booking status");
        for (UpdateBookingSlot updateBookingSlot : updateBookingSlots) {
            log.info("Update booking body: {}", updateBookingSlot);
            updateBookingSlot.setStatus(BookingStatus.BOOKED);
            try {
                courtClient.updateBookingSlot(updateBookingSlot);
            } catch (FeignException e) {
                log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
            }
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

        OrderResponse response = orderMapper.toOrderResponse(savedOrder);
        response.setQrcode(qrCodeResponse.getQrCode());
        redisTemplate.opsForValue().set(PREFIX_QR + order.getId(), qrCodeResponse.getQrCode(), 6, TimeUnit.MINUTES);
        return response;
    }

    @Transactional
    public OrderResponse changeOrder(OrderRequest request, String oid) {
        // Kiểm tra chữ ký hợp lệ
        if (!GenerateString.isValidSignature(request.getTotalAmount().toString(),
                request.getPaymentAmount().toString(),
                request.getDepositAmount().toString(),
                request.getPhoneNumber(),
                request.getSignature())) {
            throw new ApiException("Payment amount not match", "AMOUNT_NOT_MATCH");
        }

        // Tìm Order theo ID
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));

        if (!order.getOrderStatus().equals("Đặt lịch thành công") &&
                !order.getOrderStatus().equals("Đổi lịch thất bại")) {
            throw new ApiException("Đơn chưa được tạo", "INVALID_ORDER");
        }
        BigDecimal amountPaid = order.getAmountPaid();
        // Lấy danh sách booking cũ
        List<UpdateBookingSlot> oldBooking = orderMapper.orderToUpdateBookingSlot(order);

        // Xóa toàn bộ OrderDetail và BookingDate cũ thủ công
        for (OrderDetail detail : order.getOrderDetails()) {
            bookingDateRepository.deleteByOrderDetailId(detail.getId()); // Xóa BookingDate
        }
        orderDetailRepository.deleteByOrderId(order.getId());
        order.getOrderDetails().clear();
        // Cập nhật Order từ request
        orderMapper.updateOrderFromRequest(request, order);

        // Cập nhật các thuộc tính bổ sung
        order.setAmountPaid(amountPaid);
        order.setOrderStatus("Thay đổi lịch đặt");
        order.setPaymentTimeout(LocalDateTime.now().plusMinutes(5));
        order.setTotalAmount(request.getTotalAmount());
        order.setPaymentAmount(request.getPaymentAmount());
        order.setDepositAmount(request.getDepositAmount());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : "Đơn ngày");
        if (request.getUserId() != null && !request.getUserId().isEmpty()) {
            order.setUser(userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ApiException("User not found", "USER_NOT_FOUND")));
        }

        // Lưu Order để đồng bộ hóa
        Order savedOrder = orderRepository.saveAndFlush(order);

        // Cập nhật trạng thái booking cũ về AVAILABLE
        log.info("Send update booking status to AVAILABLE");
        for (UpdateBookingSlot updateBookingSlot : oldBooking) {
            updateBookingSlot.setStatus(BookingStatus.AVAILABLE);
            log.info("Update booking body available: {}", updateBookingSlot);
            try {
                courtClient.updateBookingSlot(updateBookingSlot);
            } catch (FeignException e) {
                log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
            }
        }

        // Xử lý thanh toán (nếu có)
        if (request.getPaymentAmount() != null && request.getPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            savedOrder.setPaymentAmount(request.getPaymentAmount());
            savedOrder.setPaymentStatus(savedOrder.getPaymentStatus() != null ? savedOrder.getPaymentStatus() : "Chưa đặt cọc");
            String billCode = "PM" + GenerateString.generateRandomString(13);
            savedOrder.setBillCode(billCode);

            CreateQrRequest qrRequest = CreateQrRequest.builder()
                    .billCode(billCode)
                    .amount(request.getPaymentAmount().doubleValue())
                    .signature(encrypt(request.getPaymentAmount().doubleValue(), billCode, checkSum))
                    .build();
            saveBookingSlot(orderMapper.toUpdateBookingSlotList(request), billCode);

            MbQrCodeResponse qrCodeResponse;
            try {
                qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
            } catch (FeignException e) {
                throw new ApiException("Failed to create QR", "API_MB_CREATE_QR_ERROR");
            }

            TransactionRequest transaction = TransactionRequest.builder()
                    .orderId(oid)
                    .amount(request.getAmountPaid())
                    .paymentStatus(savedOrder.getPaymentStatus())
                    .billCode(billCode)
                    .build();
            saveTransactionToRedis(transaction);

            schedulePaymentCheck(billCode);

            Order finalOrder = orderRepository.save(savedOrder);
            OrderResponse response = orderMapper.toOrderResponse(finalOrder);
            response.setQrcode(qrCodeResponse.getQrCode());
            redisTemplate.opsForValue().set(PREFIX_QR + finalOrder.getId(), qrCodeResponse.getQrCode(), 6, TimeUnit.MINUTES);

            List<UpdateBookingSlot> updateBookingSlots = orderMapper.orderToUpdateBookingSlot(finalOrder);
            for (UpdateBookingSlot updateBookingSlot : updateBookingSlots) {
                log.info("Update booking body - createQr: {}", updateBookingSlot);
                updateBookingSlot.setStatus(BookingStatus.BOOKED);
                try {
                    courtClient.updateBookingSlot(updateBookingSlot);
                } catch (FeignException e) {
                    log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                    throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
                }
            }
            return response;
        } else if (request.getPaymentAmount() != null && request.getPaymentAmount().compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal refundAmount = request.getPaymentAmount().abs();
            if (refundAmount.compareTo(savedOrder.getAmountPaid().subtract(savedOrder.getDepositAmount())) > 0) {
                throw new ApiException("Invalid refund amount", "INVALID_REFUND_AMOUNT");
            }
            try {
                processRefund(savedOrder, refundAmount);
            } catch (ApiException e) {
                throw new ApiException("Refund error", "REFUND_ERROR");
            }
            savedOrder.setPaymentAmount(null);
            savedOrder.setOrderStatus("Thay đổi lịch đặt thành công");

            Order finalOrder = orderRepository.save(savedOrder);
            List<UpdateBookingSlot> updateBookingSlots = orderMapper.orderToUpdateBookingSlot(finalOrder);
            for (UpdateBookingSlot updateBookingSlot : updateBookingSlots) {
                log.info("Update booking body - refund: {}", updateBookingSlot);
                updateBookingSlot.setStatus(BookingStatus.BOOKED);
                try {
                    courtClient.updateBookingSlot(updateBookingSlot);
                } catch (FeignException e) {
                    log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                    throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
                }
            }
            return orderMapper.toOrderResponse(finalOrder);
        } else {
            savedOrder.setOrderStatus("Thay đổi lịch đặt thành công");
            Order finalOrder = orderRepository.save(savedOrder);
            List<UpdateBookingSlot> updateBookingSlots = orderMapper.orderToUpdateBookingSlot(finalOrder);
            for (UpdateBookingSlot updateBookingSlot : updateBookingSlots) {
                log.info("Update booking body - no payment: {}", updateBookingSlot);
                updateBookingSlot.setStatus(BookingStatus.BOOKED);
                try {
                    courtClient.updateBookingSlot(updateBookingSlot);
                } catch (FeignException e) {
                    log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                    throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
                }
            }
            return orderMapper.toOrderResponse(finalOrder);
        }
    }

    private void deleteOldOrderDetail(Order order){
        orderDetailRepository.deleteAll(order.getOrderDetails());
        orderRepository.save(order);
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
        String orderStatus = order.getOrderStatus().equals("Thay đổi lịch đặt") ? "Đổi lịch thất bại" : "Hủy đặt lịch do quá giờ thanh toán";

        order.setOrderStatus(orderStatus);
        orderRepository.save(order);
        sendNoti(orderStatus.toUpperCase(),"Lịch của bạn đã bị hủy do quá thời gian chờ thanh toán.", order );

        log.info("Order {} has been cancelled due to payment timeout.", order.getId());
        log.info("Update order status");
        // Cập nhật trạng thái sân
        List<UpdateBookingSlot> updateBookingSlots = getBookingSlot(billCode);
        if (order.getOrderType().equals("Đơn ngày")) {
            for (UpdateBookingSlot updateBookingSlot : updateBookingSlots) {
                updateBookingSlot.setStatus(BookingStatus.AVAILABLE);
                log.info("Update booking body: {}", updateBookingSlot);

                try {
                    courtClient.updateBookingSlot(updateBookingSlot);
                } catch (FeignException e) {
                    log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                    throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
                }
            }
        }
    }

    public void paymentNotification(NotificationResponse response) {
        try {
            log.info("Notification payment response - {}", response.getData());
            BigDecimal amount = new BigDecimal(response.getData().getDebitAmount());
            TransactionRequest transactionRequest = getTransactionRedis(response.getData().getReferenceLabelCode());
            Order order = orderRepository.findById(transactionRequest.getOrderId()).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));

            String orderStatus = order.getOrderStatus().equals("Thay đổi lịch đặt") ? "Thay đổi lịch đặt thành công" : "Đặt lịch thành công";
            order.setOrderStatus(orderStatus);
            String paymentStatus = order.getPaymentStatus().equals("Chưa đặt cọc") ? "Đã đặt cọc" : "Đã thanh toán";
            order.setPaymentStatus(paymentStatus);
            order.setAmountPaid(order.getAmountPaid() != null ? order.getAmountPaid().add(amount)  : amount);
            orderRepository.save(order);
            socketHandler.sendNotification(NotificationResponse.builder()
                    .key(order.getId())
                    .resDesc("Payment successfully")
                    .resCode("200")
                    .build());
            Transaction transaction = Transaction.builder()
                    .order(order)
                    .amount(amount)
                    .paymentStatus(paymentStatus)
                    .billCode(response.getData().getReferenceLabelCode())
                    .ftCode(response.getData().getFtCode())
                    .createDate(LocalDateTime.now())
                    .status("Thành công")
                    .courtId(order.getCourtId())
                    .build();
            transactionRepository.save(transaction);
            if (order.getUser() != null) {
                String email = order.getUser().getEmail();
                if (email != null) {
//                try {
                    emailService.sendBookingConfirmationEmail(email, orderMapper.toOrderResponse(order));
//                } catch (MessagingException e) {
//                    log.error("Send email to {} error - message - {}" , email, e.getMessage());
//                }
                }
            }

            if(order.getOrderType().equals("Đơn cố định")){
                courtClient.synchronous(order.getCourtId());
            }

            sendNoti(orderStatus.toUpperCase(), "Cảm ơn bạn đã đặt lịch , vui lòng kiểm tra lịch đặt và đến sân đúng giờ", order);
            sendNotiManagerAndStaff("BẠN CÓ " + orderStatus.toUpperCase(), "Bạn có lịch đặt mới hãy kiểm tra lịch đặt của khách", order);
            deleteTransactionRedis(response.getData().getReferenceLabelCode());
        } catch (Exception e) {
            throw new ApiException("NOTIFY_PAYMENT_FAIL", "Fail on process in payment service!");
        }

    }


//    public void paymentNotification(PaymentData paymentData){
//        TransactionRequest transactionRequest = getTransactionRedis(paymentData.getBillCode());
//        Order order = orderRepository.findById(transactionRequest.getOrderId()).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));
//        order.setOrderStatus("Đặt lịch thành công");
//        String paymentStatus = order.getPaymentStatus().equals("Chưa đặt cọc") ? "Đã đặt cọc" : "Đã thanh toán";
//        order.setPaymentStatus(paymentStatus);
//        orderRepository.save(order);
//        socketHandler.sendNotification(NotificationResponse.builder()
//                        .key(order.getId())
//                        .resDesc("Payment successfully")
//                        .resCode("200")
//                .build());
//        Transaction transaction = Transaction.builder()
//                .order(order)
//                .amount(transactionRequest.getAmount())
//                .paymentStatus(paymentStatus)
//                .billCode(paymentData.getBillCode())
//                .ftCode(paymentData.getFtCode())
//                .createDate(LocalDateTime.now())
//                .status("Success")
//                .build();
//        transactionRepository.save(transaction);
//        if(order.getUser() != null){
//            String email = order.getUser().getEmail();
//            if(email != null){
////                try {
//                    emailService.sendBookingConfirmationEmail(email,orderMapper.toResponse(order));
////                } catch (MessagingException e) {
////                    log.error("Send email to {} error - message - {}" , email, e.getMessage());
////                }
//            }
//        }
//
////        sendNoti("ĐẶT LỊCH THÀNH CÔNG","Cảm ơn bạn đã đặt lịch , vui lòng kiểm tra lịch đặt và đến sân đúng giờ", order );
//        deleteTransactionRedis(paymentData.getBillCode());
//    }

    public void sendNoti(String title, String des, Order order){
        List<String> fcmTokens = order.getUser() != null ?  fcmService.getTokens(order.getUser().getId()) : fcmService.getTokens(order.getPhoneNumber());
        if(fcmTokens == null || fcmTokens.isEmpty()) fcmTokens = fcmService.getTokens(order.getPhoneNumber());
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .title(title)
                .description(des)
                .createAt(LocalDateTime.now())
                .status("send")
                .notificationData(NotiData.builder()
                        .orderId(order.getId())
                        .build())
                .build();
        NotificationRequest notification = notificationService.saveNotification(notificationRequest, order.getPhoneNumber());
        fcmService.sendNotification(fcmTokens, notification);
    }

    public void sendNotiManagerAndStaff(String title, String des, Order order) {
        List<User> users = userRepository.findByCourtId(order.getCourtId());

        for (User user : users) {
            List<String> fcmTokens = fcmService.getTokens(user.getId());

            NotificationRequest notificationRequest = NotificationRequest.builder()
                    .title(title)
                    .description(des)
                    .createAt(LocalDateTime.now())
                    .status("send")
                    .notificationData(NotiData.builder()
                            .orderId(order.getId())
                            .build())
                    .build();

            NotificationRequest notification = notificationService.saveNotificationManagerAndStaff(notificationRequest, user);

            fcmService.sendNotification(fcmTokens, notification);
        }
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

    public void saveBookingSlot(List<UpdateBookingSlot> bookingSlots, String billCode) {
        try {
            String key = PREFIX + billCode;
            String value = redisObjectMapper.writeValueAsString(bookingSlots);
            redisTemplate.opsForValue().set(key, value, 6, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize booking slots", e);
        }
    }

    // Get List<UpdateBookingSlot> from Redis
    public List<UpdateBookingSlot> getBookingSlot(String billCode) {
        try {
            String key = PREFIX + billCode;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return redisObjectMapper.readValue(value, new TypeReference<List<UpdateBookingSlot>>() {});
            }
            return Collections.emptyList(); // Trả về danh sách rỗng nếu không tìm thấy
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize booking slots", e);
        }
    }

    public List<OrderResponse> getOrders(String value) {
        List<Order> orders;

        orders = orderRepository.findByPhoneNumberOrUserId(value);

        return orderMapper.toOrderResponses(orders);
    }

    public OrderResponse getOrderById (String oid){
        OrderResponse response =  orderMapper.toOrderResponse(orderRepository.findById(oid).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND")));
        response.setQrcode(redisTemplate.opsForValue().get(PREFIX_QR + oid));
        return response;
    }

    public OrderResponse cancelOrder(String orderId){
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));

//        if(order.getPaymentTimeout().isAfter(LocalDateTime.now())){
//            throw  new ApiException("Order was timeout", "ORDER_TIMEOUT");
//        }

        order.setOrderStatus("Hủy đặt lịch");
        order.setPaymentStatus(order.getPaymentStatus().equals("Chưa đặt cọc") ? "Chưa đặt cọc" : order.getPaymentStatus());
        if(order.getAmountPaid() != null && order.getAmountPaid().compareTo(order.getDepositAmount()) > 0){
            try {
                processRefund(order, order.getPaymentAmount().subtract(order.getDepositAmount()));
            }catch (Exception e){
                throw new ApiException("Refund error", "REFUND_ERROR");
            }
        }


        for (UpdateBookingSlot updateBookingSlot : orderMapper.orderToUpdateBookingSlot(order)) {
            updateBookingSlot.setStatus(BookingStatus.AVAILABLE);
            log.info("Update booking body: {}", updateBookingSlot);

            try {
                courtClient.updateBookingSlot(updateBookingSlot);
            } catch (FeignException e) {
                log.error("Failed to update booking slot: {}", updateBookingSlot, e);
                throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
            }
        }
        orderRepository.save(order);
        sendNoti("HỦY LỊCH ĐẶT THÀNH CÔNG", "Lịch đặt của bạn đã được hủy.", order);
        sendNotiManagerAndStaff("KHÁCH HÀNG ĐÃ HỦY LỊCH ĐẶT" ,"Kiểm tra lại lịch đặt và hoàn tiền nếu có", order);
        deleteTransactionRedis(order.getBillCode());
        return orderMapper.toOrderResponse(order);
    }

    public void testFcm(String key){
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .title("test")
                .description("test")
                .createAt(LocalDateTime.now())
                .status("send")
                .build();
        fcmService.sendNotification(fcmService.getTokens(key), notificationRequest);

    }

//    @PreAuthorize("hasRole('ADMIN')")
    public void refund(MbVietQrRefundWithAmountRequest refundWithAmountRequest){
        try {
            paymentClient.refund(refundWithAmountRequest, apiKey, clientId);
        } catch (FeignException e) {
            throw new ApiException("Failed to refund", "API_MB_REFUND_ERROR");
        }
    }





    public UpdateBookingSlot getBookedSlot(String courtId , LocalDate bookingDate){
        List<String> statuses = Arrays.asList("Đặt lịch thành công", "Thay đổi lịch đặt thành công");
        List<Order> orders =  orderRepository.findByOrderStatusInAndCourtIdAndBookingDate(statuses, courtId, bookingDate);
        if(orders==null ||orders.isEmpty() ){
            return null;
        }
        return orderMapper.mapToUpdateBookingSlot(orders,bookingDate);
    }




    public List<OrderDetail> getOrderDetailsByBookingDate(LocalDate bookingDate) {
        return orderDetailRepository.findAllByBookingDate(bookingDate);
    }

//    public List<Order> getUnsettledOrders() {
//        return orderRepository.findByOrderStatusNotAndPaymentStatusAndSettlementTimeLessThanEqual(
//                "Đã hoàn thành", "Đã thanh toán", LocalDateTime.now()
//        );
//    }

    public List<Order> getOrderRefund(){
        return orderRepository.findByPaymentStatus("Đợi hoàn tiền");
    }



    public void refundByJob(Order order) {
        try {
            processRefund(order, order.getAmountPaid().subtract(order.getDepositAmount()));
        } catch (ApiException e) {
            log.error("Refund error - {}", order.getId());
        }
        orderRepository.save(order);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public void refundByAdmin(String orderId , BigDecimal refundAmount){
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));
        processRefund(order , refundAmount);
    }

    public void processRefund(Order order, BigDecimal refundAmount) {

        // Lấy danh sách transactions cần refund đã được tính toán trước
        List<Transaction> refundTransactions;
        try {
            refundTransactions = findRefundTransactionsByBillCode(order.getId(), refundAmount);
        } catch (ApiException e) {
            saveRefundFailure(order);
            throw e;
        }

        try {
            refundTransactionsAndLog(refundTransactions, refundAmount, order);
            saveRefundSuccess(order, refundAmount);
        } catch (ApiException e) {
            saveRefundFailure(order);
            throw e;
        }
    }

    public void refundTransactionsAndLog(List<Transaction> refundTransactions, BigDecimal refundAmount, Order order) {
        BigDecimal remainingRefund = refundAmount;

        for (Transaction transaction : refundTransactions) {
            if (remainingRefund.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal refundForTransaction = transaction.getAmount().min(remainingRefund);

            MbVietQrRefundWithAmountRequest refundRequest = MbVietQrRefundWithAmountRequest.builder()
                    .amount(refundForTransaction.toString())
                    .billCode(transaction.getBillCode())
                    .build();

            try {
                // Thực hiện refund cho transaction
                refund(refundRequest);
                remainingRefund = remainingRefund.subtract(refundForTransaction);

                // Lưu transaction log hoàn tiền cho từng giao dịch
                Transaction refundLog = Transaction.builder()
                        .order(order)
                        .amount(refundForTransaction)
                        .paymentStatus("Hoàn tiền")
                        .billCode(transaction.getBillCode())
                        .createDate(LocalDateTime.now())
                        .status("Hoàn tiền thành công")
                        .courtId(order.getCourtId())
                        .build();
                transactionRepository.save(refundLog);

            } catch (ApiException e) {
                throw new ApiException("Refund thất bại cho billCode: " + transaction.getBillCode(), "API_MB_REFUND_ERROR");
            }
        }

        if (remainingRefund.compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException("Không thể hoàn đủ số tiền mong muốn. Còn thiếu: " + remainingRefund, "REFUND_AMOUNT_NOT_FULLFILLED");
        }
    }

    public void saveRefundFailure(Order order) {
        order.setPaymentStatus("Hoàn tiền thất bại");
        sendNoti("HOÀN TIỀN THẤT BẠI", "Vui lòng liên hệ chúng tôi để được hỗ trợ.", order);
        sendNotiManagerAndStaff("HOÀN TIỀN THẤT BẠI", "Kiểm tra đơn hàng và trạng thái hoàn tiền", order);
    }

    public void saveRefundSuccess(Order order, BigDecimal refundAmount) {
        order.setPaymentStatus("Đã hoàn tiền");
        order.setAmountRefund(refundAmount);
        sendNoti("HOÀN TIỀN THÀNH CÔNG", "Chúng tôi đã hoàn " + refundAmount + ", vui lòng kiểm tra tài khoản.", order);
        sendNotiManagerAndStaff("HOÀN TIỀN THÀNH CÔNG", "Kiểm tra đơn hàng và số tiền đã hoàn",order);
    }


    public List<Transaction> findRefundTransactionsByBillCode(String orderId, BigDecimal refundAmount) {
        List<Transaction> transactions = findUniqueBillCodeTransactions(orderId);

        if (transactions.isEmpty()) {
            throw new ApiException("Không tìm thấy transaction nào hợp lệ theo orderId: " + orderId, "CANNOT_REFUND");
        }

        // Tính tổng amount
        BigDecimal totalAmount = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(refundAmount) < 0) {
            throw new ApiException("Tổng amount nhỏ hơn refundAmount, không thể hoàn refund.", "CANNOT_REFUND");
        }

        // 1. Transaction amount = refundAmount
        Optional<Transaction> exactMatch = transactions.stream()
                .filter(t -> t.getAmount().compareTo(refundAmount) == 0)
                .findFirst();

        if (exactMatch.isPresent()) {
            return List.of(exactMatch.get());
        }

        // 2. Gần nhất
        Transaction closestTransaction = transactions.stream()
                .min(Comparator.comparing(t -> t.getAmount().subtract(refundAmount).abs()))
                .orElse(null);

        if (closestTransaction != null && closestTransaction.getAmount().compareTo(refundAmount) > 0) {
            return List.of(closestTransaction);
        }

        // 3. Tìm tổ hợp gần nhất
        List<Transaction> bestCombination = findBestCombination(transactions, refundAmount);

        if (bestCombination.isEmpty()) {
            throw new ApiException("Không thể refund", "CANNOT_REFUND");
        }

        return bestCombination;
    }


    private List<Transaction> findBestCombination(List<Transaction> transactions, BigDecimal targetAmount) {
        List<Transaction> bestCombination = new ArrayList<>();
        findBestCombinationHelper(transactions, 0, targetAmount, new ArrayList<>(), bestCombination);
        return bestCombination;
    }

    private void findBestCombinationHelper(List<Transaction> transactions, int index, BigDecimal targetAmount,
                                           List<Transaction> currentList, List<Transaction> bestCombination) {
        BigDecimal currentSum = currentList.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (currentSum.compareTo(targetAmount) <= 0) {
            if (bestCombination.isEmpty() ||
                    currentSum.subtract(sum(bestCombination)).abs().compareTo(targetAmount.subtract(sum(bestCombination)).abs()) < 0) {
                bestCombination.clear();
                bestCombination.addAll(new ArrayList<>(currentList));
            }
        }

        if (index == transactions.size()) {
            return;
        }

        // Không chọn transaction hiện tại
        findBestCombinationHelper(transactions, index + 1, targetAmount, currentList, bestCombination);

        // Chọn transaction hiện tại
        currentList.add(transactions.get(index));
        findBestCombinationHelper(transactions, index + 1, targetAmount, currentList, bestCombination);
        currentList.remove(currentList.size() - 1);
    }

    private BigDecimal sum(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }



    public List<Transaction> findUniqueBillCodeTransactions(String orderId) {
        List<Transaction> transactions = transactionRepository.findByOrderId(orderId);

        // Đếm số lần xuất hiện của mỗi billCode
        Map<String, Long> billCodeCount = transactions.stream()
                .filter(t -> t.getBillCode() != null)
                .collect(Collectors.groupingBy(Transaction::getBillCode, Collectors.counting()));

        // Lọc các transaction có billCode chỉ xuất hiện 1 lần và sort theo amount DESC
        return transactions.stream()
                .filter(t -> t.getBillCode() != null && billCodeCount.get(t.getBillCode()) == 1)
                .sorted(Comparator.comparing(Transaction::getAmount).reversed())
                .toList();
    }


    /**
     * Kiểm tra những courtSlot nào bị Order hoặc Maintenance
     */
    public Map<String, Object> getInvalidCourtSlots(String courtId,
                                                    List<LocalDate> bookingDates,
                                                    LocalTime startTime,
                                                    LocalTime endTime) {
        // Lấy danh sách lịch bảo trì từ FeignClient
        Map<String, List<LocalDate>> maintenanceCourtSlots = courtClient.getInvalidCourtSlots(
                courtId, bookingDates, startTime, endTime).getBody();

        // Lấy danh sách lịch đã đặt từ orderRepository
        List<Object[]> bookedSlots = orderRepository.findBookedCourtSlots(
                courtId, bookingDates, startTime, endTime, Arrays.asList("Đặt lịch thành công", "Thay đổi lịch đặt thành công"));

        // Kết hợp lịch bảo trì & lịch đã đặt vào invalidCourtSlots
        Map<String, List<LocalDate>> invalidCourtSlots = new HashMap<>();

        // Thêm lịch bảo trì vào invalidCourtSlots
        if (maintenanceCourtSlots != null && !maintenanceCourtSlots.isEmpty()) {
            maintenanceCourtSlots.forEach((courtSlotId, dates) ->
                    invalidCourtSlots.computeIfAbsent(courtSlotId, k -> new ArrayList<>()).addAll(dates)
            );
        }

        // Thêm lịch đã đặt vào invalidCourtSlots
        for (Object[] booking : bookedSlots) {
            String courtSlotId = (String) booking[0];
            LocalDate date = (LocalDate) booking[1];
            invalidCourtSlots.computeIfAbsent(courtSlotId, k -> new ArrayList<>()).add(date);
        }
        // Lấy danh sách CourtSlot khả dụng
        List<String> availableCourtSlots = courtClient.getCourtSlotIdsByCourtId(courtId).getBody()
                .stream()
                .filter(slotId -> !invalidCourtSlots.containsKey(slotId))
                .collect(Collectors.toList());

        // Trả về cả danh sách slot không hợp lệ & hợp lệ
        Map<String, Object> response = new HashMap<>();
        response.put("invalidCourtSlots", invalidCourtSlots);
        response.put("availableCourtSlots", availableCourtSlots);

        return response;
    }



    /**
     * Lấy danh sách các ngày thuộc những thứ người dùng đã chọn trong khoảng startDate - endDate
     */
    public List<LocalDate> getBookingDatesFromDaysOfWeek(LocalDate startDate, LocalDate endDate, String daysOfWeek) {
        Set<DayOfWeek> selectedDays = Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());

        List<LocalDate> bookingDates = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (selectedDays.contains(currentDate.getDayOfWeek())) {
                bookingDates.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }

        return bookingDates;
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
public OrderResponse createFixedBooking(FixedBookingRequest request) {
    // Tạo đơn hàng
    Order order = new Order();
    order.setCustomerName(request.getCustomerName());
    order.setPhoneNumber(request.getPhoneNumber());
    order.setCourtId(request.getCourtId());
    order.setCourtName(request.getCourtName());
    order.setAddress(request.getAddress());
    order.setOrderStatus("Đang xử lý");
    order.setOrderType(request.getOrderType() != null ? request.getOrderType() : "Đơn cố định");
    order.setPaymentStatus(request.getPaymentStatus() != null ? request.getPaymentStatus() : "Chưa thanh toán");
    order.setNote(request.getNote());
    if (request.getUserId() != null && !request.getUserId().isEmpty()) {
        order.setUser(userRepository.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found")));
    }

    // Lưu order trước để có ID
    order = orderRepository.save(order);

    // Prepare OrderDetails
    List<LocalDate> bookingDates = getBookingDatesFromDaysOfWeek(
            request.getStartDate(), request.getEndDate(), request.getSelectedDays()
    );

    List<OrderDetail> allOrderDetails = new ArrayList<>();

    for (String courtSlotName : request.getSelectedCourtSlots()) {
        String courtSlotId = courtClient.getCourtSlotIdByName(request.getCourtId(), courtSlotName);

        // Fixed dates
        List<LocalDate> fixedDates = bookingDates.stream()
                .filter(date -> request.getFlexibleCourtSlotFixes() == null ||
                        !request.getFlexibleCourtSlotFixes().containsKey(date.toString()))
                .collect(Collectors.toList());

        if (!fixedDates.isEmpty()) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrder(order);
            orderDetail.setCourtSlotId(courtSlotId);
            orderDetail.setCourtSlotName(courtSlotName);
            orderDetail.setStartTime(request.getStartTime());
            orderDetail.setEndTime(request.getEndTime());
            orderDetail.setPrice(null);

            List<BookingDate> fixedBookingDates = new ArrayList<>();
            for (LocalDate date : fixedDates) {
                BookingDate bookingDate = new BookingDate();
                bookingDate.setBookingDate(date);
                bookingDate.setOrderDetail(orderDetail);
                fixedBookingDates.add(bookingDate);
            }
            orderDetail.setBookingDates(fixedBookingDates);
            allOrderDetails.add(orderDetail);
        }

        // Flexible dates
        if (request.getFlexibleCourtSlotFixes() != null) {
            for (Map.Entry<String, String> entry : request.getFlexibleCourtSlotFixes().entrySet()) {
                LocalDate conflictDate = LocalDate.parse(entry.getKey());
                String fixedCourtSlotName = entry.getValue();
                String fixedCourtSlotId = courtClient.getCourtSlotIdByName(request.getCourtId(), fixedCourtSlotName);

                OrderDetail flexibleDetail = new OrderDetail();
                flexibleDetail.setOrder(order);
                flexibleDetail.setCourtSlotId(fixedCourtSlotId);
                flexibleDetail.setCourtSlotName(fixedCourtSlotName);
                flexibleDetail.setStartTime(request.getStartTime());
                flexibleDetail.setEndTime(request.getEndTime());
                flexibleDetail.setPrice(null);

                BookingDate bookingDate = new BookingDate();
                bookingDate.setBookingDate(conflictDate);
                bookingDate.setOrderDetail(flexibleDetail);

                flexibleDetail.setBookingDates(new ArrayList<>(Collections.singletonList(bookingDate)));
                allOrderDetails.add(flexibleDetail);
            }
        }
    }
    
    orderDetailRepository.saveAll(allOrderDetails);

    BigDecimal amount = courtClient.calculateTotalPayment(BookingPaymentRequest.builder()
            .courtId(request.getCourtId())
            .bookingDates(bookingDates)
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .build()).multiply(BigDecimal.valueOf(request.getSelectedCourtSlots().size()));
    String billCode = "PM" + GenerateString.generateRandomString(13);

    order.setBillCode(billCode);
    order.setPaymentAmount(amount);
    order.setTotalAmount(amount);
    order = orderRepository.save(order);
    order.setOrderDetails(allOrderDetails);
    CreateQrRequest qrRequest = CreateQrRequest.builder()
            .billCode(billCode)
            .amount(amount.doubleValue())
            .signature(encrypt(amount.doubleValue(), billCode, checkSum))
            .build();

    MbQrCodeResponse qrCodeResponse;
    try {
        qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
    } catch (FeignException e) {
        throw new ApiException(e.getMessage(), "API_MB_CREATE_QR_ERROR");
    }
    log.info("send update booking status");

    TransactionRequest transaction = TransactionRequest.builder()
            .orderId(order.getId())
            .amount(amount)
            .paymentStatus(order.getPaymentStatus())
            .billCode(billCode)
            .build();

    saveTransactionToRedis(transaction);
    schedulePaymentCheck(billCode);

    OrderResponse response = orderMapper.toOrderResponse(order);
    response.setQrcode(qrCodeResponse.getQrCode());
    redisTemplate.opsForValue().set(PREFIX_QR + order.getId(), qrCodeResponse.getQrCode(), 6, TimeUnit.MINUTES);
    return response;
}

public BigDecimal getPaymentAmount(String courtId, List<LocalDate> dates, LocalTime start, LocalTime end){
        return  courtClient.calculateTotalPayment(BookingPaymentRequest.builder()
                .courtId(courtId)
                .bookingDates(dates)
                .startTime(start)
                .endTime(end)
                .build());
}

//    private OrderResponse mapToOrderResponse(Order order, List<OrderDetail> fixedOrderDetails, List<OrderDetail> flexibleOrderDetails) {
//        return OrderResponse.builder()
//                .id(order.getId())
//                .courtName(order.getCourtName())
//                .customerName(order.getCustomerName())
//                .userId(order.getUser().getId())
//                .phoneNumber(order.getPhoneNumber())
//                .orderType(order.getOrderType())
//                .note(order.getNote())
//                .address(order.getAddress())
//                .orderStatus(order.getOrderStatus())
//                .paymentStatus(order.getPaymentStatus())
//                .totalAmount(order.getPaymentAmount())
//                .paymentAmount(order.getPaymentAmount())
//                .depositAmount(order.getDepositAmount())
//                .paymentTimeout(order.getPaymentTimeout())
//                .fixedOrderDetails(mapOrderDetails(fixedOrderDetails))
//                .flexibleOrderDetails(mapOrderDetails(flexibleOrderDetails))
//                .build();
//    }
//
//    private List<FixedResponse> mapOrderDetails(List<OrderDetail> orderDetails) {
//        return orderDetails.stream().map(orderDetail ->
//                new FixedResponse(
//                        orderDetail.getCourtSlotName(),
//                        orderDetail.getStartTime(),
//                        orderDetail.getEndTime(),
//                        orderDetail.getBookingDates().stream().map(BookingDate::getBookingDate).collect(Collectors.toList())
//                )).collect(Collectors.toList());
//    }

}

