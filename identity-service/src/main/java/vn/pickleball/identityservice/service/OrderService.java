package vn.pickleball.identityservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import vn.pickleball.identityservice.dto.response.MbQrCodeResponse;
import vn.pickleball.identityservice.dto.response.OrderResponse;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.OrderDetail;
import vn.pickleball.identityservice.entity.Transaction;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.mapper.OrderMapper;
import vn.pickleball.identityservice.mapper.TransactionMapper;
import vn.pickleball.identityservice.repository.OrderDetailRepository;
import vn.pickleball.identityservice.repository.OrderRepository;
import vn.pickleball.identityservice.repository.TransactionRepository;
import vn.pickleball.identityservice.repository.UserRepository;
import vn.pickleball.identityservice.utils.GenerateString;
import vn.pickleball.identityservice.websockethandler.NotificationWebSocketHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
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
    private final FCMService fcmService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OrderDetailRepository orderDetailRepository;

    private static final String TRANSACTION_KEY_PREFIX = "transaction:";
    private static final String PREFIX = "bookingslot:";

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
                request.getBookingDate().toString(),
                request.getSignature())){
            throw new ApiException("Payment amount not match", "AMOUNT_NOT_MATCH");
        }

        Order order = orderMapper.toEntity(request);
        List<OrderDetail> orderDetails = orderMapper.toEntity(request.getOrderDetails());

        orderDetails.forEach(detail -> detail.setOrder(order));
        order.setPaymentTimeout(LocalDateTime.now().plusMinutes(5));
        order.setOrderDetails(orderDetails);
        order.setTotalAmount(request.getTotalAmount());
        order.setPaymentAmount(request.getPaymentAmount());
        order.setDepositAmount(request.getDepositAmount());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : "Đơn ngày");
        order.setPaymentStatus(request.getPaymentStatus() != null ? request.getPaymentStatus() : "Chưa đặt cọc");
        if(request.getUserId() != null && !request.getUserId().isEmpty()){
            order.setUser(userRepository.findById(request.getUserId()).orElseThrow(() -> null));
        }
        LocalTime latestEndTime = Collections.max(request.getOrderDetails(),
                        Comparator.comparing(OrderDetailRequest::getEndTime))
                .getEndTime();
        order.setSettlementTime(LocalDateTime.of(request.getBookingDate(),latestEndTime.plusHours(1)));

        Double amount = request.getPaymentAmount().doubleValue();
        String billCode = "PM" + GenerateString.generateRandomString(13);

        order.setBillCode(billCode);

        // Tạo QR thanh toán
        CreateQrRequest qrRequest = CreateQrRequest.builder()
                .billCode(billCode)
                .amount(amount)
                .signature(encrypt(amount, billCode, checkSum))
                .build();
        saveBookingSlot(orderMapper.toUpdateBookingSlot(request), billCode);
        MbQrCodeResponse qrCodeResponse;
        try {
            qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
        } catch (FeignException e) {
            throw new ApiException(e.getMessage(), "API_MB_CREATE_QR_ERROR");
        }
        log.info("send update booking status");
        try {
            courtClient.updateBookingSlots(orderMapper.toUpdateBookingSlot(request));
        } catch (FeignException e) {
            throw new ApiException(e.getMessage(), "API_UPDATE_BOOKING_SLOT_ERROR");
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

    public OrderResponse changeOrder(OrderRequest request, String oid) {
        // Tìm Order cũ
        if(!GenerateString.isValidSignature(request.getTotalAmount().toString(),
                request.getPaymentAmount().toString(),
                request.getDepositAmount().toString(),
                request.getBookingDate().toString(),
                request.getSignature())){
            throw new ApiException("Payment amount not match", "AMOUNT_NOT_MATCH");
        }
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND"));

        // Lưu thông tin booking cũ để cập nhật trạng thái
        UpdateBookingSlot oldBooking = orderMapper.orderToUpdateBookingSlot(order);

        // Xóa các OrderDetail cũ
        List<OrderDetail> oldOrderDetails = new ArrayList<>(order.getOrderDetails());
        for (OrderDetail oldDetail : oldOrderDetails) {
            order.getOrderDetails().remove(oldDetail); // Xóa khỏi danh sách
            orderDetailRepository.delete(oldDetail); // Xóa khỏi cơ sở dữ liệu
        }

        // Thêm các OrderDetail mới
        List<OrderDetail> orderDetails = orderMapper.toEntity(request.getOrderDetails());
        orderDetails.forEach(detail -> {
            detail.setOrder(order); // Thiết lập mối quan hệ
            order.getOrderDetails().add(detail); // Thêm vào danh sách
        });

        // Cập nhật thông tin Order
        order.setOrderStatus("Thay đổi lịch đặt");
        order.setPaymentTimeout(LocalDateTime.now().plusMinutes(5));
        order.setTotalAmount(request.getTotalAmount());

        // Cập nhật trạng thái booking cũ
        oldBooking.setStatus(BookingStatus.AVAILABLE);
        if(request.getUserId() != null && !request.getUserId().isEmpty()){
            order.setUser(userRepository.findById(request.getUserId()).orElseThrow(() -> null));
        }
        LocalTime latestEndTime = Collections.max(request.getOrderDetails(),
                        Comparator.comparing(OrderDetailRequest::getEndTime))
                .getEndTime();
        order.setSettlementTime(LocalDateTime.of(request.getBookingDate(),latestEndTime.plusHours(1)));
        // Cập nhật trạng thái booking mới
        log.info("send update booking status");
        try {
            courtClient.updateBookingSlots(oldBooking);
            courtClient.updateBookingSlots(orderMapper.toUpdateBookingSlot(request));
        } catch (FeignException e) {
            throw new ApiException("Failed to update booking slot", "API_UPDATE_BOOKING_SLOT_ERROR");
        }

        // Xử lý thanh toán (nếu có)
        if (request.getPaymentAmount() != null && request.getPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentAmount(request.getPaymentAmount());
            order.setPaymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus() : "Chưa đặt cọc");
            Double amount = request.getPaymentAmount().doubleValue();
            String billCode = "PM" + GenerateString.generateRandomString(13);
            order.setBillCode(billCode);

            // Tạo QR thanh toán
            CreateQrRequest qrRequest = CreateQrRequest.builder()
                    .billCode(billCode)
                    .amount(amount)
                    .signature(encrypt(amount, billCode, checkSum))
                    .build();
            saveBookingSlot(orderMapper.toUpdateBookingSlot(request), billCode);

            MbQrCodeResponse qrCodeResponse;
            try {
                qrCodeResponse = paymentClient.createQr(qrRequest, apiKey, clientId);
            } catch (FeignException e) {
                throw new ApiException("Failed to create QR", "API_MB_CREATE_QR_ERROR");
            }

            // Lưu thông tin giao dịch
            TransactionRequest transaction = TransactionRequest.builder()
                    .orderId(oid)
                    .amount(request.getAmountPaid())
                    .paymentStatus(order.getPaymentStatus())
                    .billCode(billCode)
                    .build();
            saveTransactionToRedis(transaction);

            // Lên lịch kiểm tra thanh toán sau 5 phút
            schedulePaymentCheck(billCode);

            // Lưu Order và trả về response
            Order savedOrder = orderRepository.save(order);
            OrderResponse response = orderMapper.toResponse(savedOrder);
            response.setQrcode(qrCodeResponse.getQrCode());
            return response;
        }else if (request.getPaymentAmount() != null && request.getPaymentAmount().compareTo(BigDecimal.ZERO) < 0) {

            BigDecimal refundAmount = request.getPaymentAmount().abs();

            if(refundAmount.compareTo(order.getAmountPaid().subtract(order.getDepositAmount())) > 0 ){
                throw  new ApiException("Invalid refund amount", "INVALID_REFUND    _AMOUNT");
            }
            try {
                this.refund(MbVietQrRefundWithAmountRequest.builder()
                        .amount(refundAmount.toString())
                        .billCode(order.getBillCode())
                        .build());
            }catch (Exception e){
                throw new ApiException("Refund error", "REFUND_ERROR");
            }


            order.setAmountRefund(refundAmount);
            order.setPaymentStatus("Đã hoàn tiền");
            Transaction transaction = Transaction.builder()
                    .order(order)
                    .amount(refundAmount)
                    .paymentStatus("Hoàn tiền")
                    .billCode(order.getBillCode())
                    .createDate(LocalDateTime.now())
                    .status("Hoàn tiền thành công")
                    .courtId(order.getCourtId())
                    .build();
            transactionRepository.save(transaction);

            // Lưu Order và trả về response
            Order savedOrder = orderRepository.save(order);
            OrderResponse response = orderMapper.toResponse(savedOrder);

            sendNoti("Hoàn tiền thành công","Vui lòng kiểm tra đơn của bạn , và tài khoản ngân hàng", order );

            return response;
        } else {
            // Lưu Order và trả về response
            Order savedOrder = orderRepository.save(order);
            return orderMapper.toResponse(savedOrder);
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
        String orderStatus = order.getOrderStatus().equals("Thay đổi lịch đặt") ? "Hủy đổi lịch do quá giờ thanh toán" : "Hủy đặt lịch do quá giờ thanh toán";

        order.setOrderStatus(orderStatus);
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
        sendNoti(orderStatus.toUpperCase(),"Lịch của bạn đã bị hủy do quá thời gian chờ thanh toán.", order );

        log.info("Order {} has been cancelled due to payment timeout.", order.getId());
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
                    emailService.sendBookingConfirmationEmail(email, orderMapper.toResponse(order));
//                } catch (MessagingException e) {
//                    log.error("Send email to {} error - message - {}" , email, e.getMessage());
//                }
                }
            }

            sendNoti(orderStatus.toUpperCase(), "Cảm ơn bạn đã đặt lịch , vui lòng kiểm tra lịch đặt và đến sân đúng giờ", order);
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
                        .dateBooking(order.getBookingDate())
                        .build())
                .build();
        NotificationRequest notification = notificationService.saveNotification(notificationRequest, order.getPhoneNumber());
        fcmService.sendNotification(fcmTokens, notification);
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

    public List<OrderResponse> getOrders(String value) {
        List<Order> orders;

        orders = orderRepository.findByPhoneNumberOrUserId(value);

        return orderMapper.toResponseList(orders);
    }

    public OrderResponse getOrderById (String oid){
        return orderMapper.toResponse(orderRepository.findById(oid).orElseThrow(() -> new ApiException("Not found Order", "ENTITY_NOT_FOUND")));
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
                refund(MbVietQrRefundWithAmountRequest.builder()
                        .billCode(order.getBillCode())
                        .amount(order.getAmountPaid().subtract(order.getDepositAmount()).toString())
                        .build());

                Transaction transaction = Transaction.builder()
                        .order(order)
                        .amount(order.getAmountPaid().subtract(order.getDepositAmount()))
                        .paymentStatus("Hoàn tiền")
                        .billCode(order.getBillCode())
                        .createDate(LocalDateTime.now())
                        .status("Hoàn tiền thành công")
                        .courtId(order.getCourtId())
                        .build();
                transactionRepository.save(transaction);
            }catch (Exception e){
                throw new ApiException("Refund error", "REFUND_ERROR");
            }
            order.setPaymentStatus("Đã hoàn tiền");
            order.setAmountRefund(order.getAmountPaid().subtract(order.getDepositAmount()));
            sendNoti("Hoàn tiền hủy lịch", "Chúng tôi đã hoàn tiền, vui lòng kiểm tra tài khoản", order);
        }

        orderRepository.save(order);

        UpdateBookingSlot updateBookingSlot = orderMapper.orderToUpdateBookingSlot(order);
        updateBookingSlot.setStatus(BookingStatus.AVAILABLE);
        log.info("Update booking body: {}", updateBookingSlot);
        try {
            courtClient.updateBookingSlots(updateBookingSlot);
        } catch (FeignException e) {
            log.error("Failed to update booking slot", e);
            throw new RuntimeException("Failed to update booking slot: " + e.getMessage());
        }

        sendNoti("HỦY LỊCH ĐẶT THÀNH CÔNG", "Lịch đặt của bạn đã được hủy.", order);
        deleteTransactionRedis(order.getBillCode());
        return orderMapper.toResponse(order);
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
        return orderMapper.ordersToUpdateBookingSlot(orders,bookingDate,courtId);
    }




    public List<OrderDetail> getOrderDetailsByBookingDate(LocalDate bookingDate) {
        return orderDetailRepository.findAllByBookingDate(bookingDate);
    }

    public List<Order> getUnsettledOrders() {
        return orderRepository.findByOrderStatusNotAndPaymentStatusAndSettlementTimeLessThanEqual(
                "Đã hoàn thành", "Đã thanh toán", LocalDateTime.now()
        );
    }

    @Transactional
    public void refundByJob(Order order) {
        try {
            refund(MbVietQrRefundWithAmountRequest.builder()
                    .billCode(order.getBillCode())
                    .amount(order.getAmountPaid().subtract(order.getDepositAmount()).toString())
                    .build());
        } catch (Exception e) {
            saveRefundFailure(order); // Lưu trạng thái thất bại trong một transaction riêng
            log.error("Refund error - {}", order.getId());
        }

        saveRefundSuccess(order); // Lưu trạng thái thành công và transaction
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRefundFailure(Order order) {
        order.setPaymentStatus("Hoàn tiền thất bại");
        orderRepository.save(order);
        sendNoti("HOÀN TIỀN THẤT BẠI", "Vui lòng liên hệ lại với chúng tôi", order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRefundSuccess(Order order) {
        order.setPaymentStatus("Đã hoàn tiền");
        order.setAmountRefund(order.getAmountPaid().subtract(order.getDepositAmount()));
        orderRepository.save(order);
        sendNoti("HOÀN TIỀN THÀNH CÔNG", "Chúng tôi đã hoàn tiền, vui lòng kiểm tra tài khoản", order);

        Transaction transaction = Transaction.builder()
                .order(order)
                .amount(order.getAmountPaid().subtract(order.getDepositAmount()))
                .paymentStatus("Hoàn tiền")
                .billCode(order.getBillCode())
                .createDate(LocalDateTime.now())
                .status("Hoàn tiền thành công")
                .courtId(order.getCourtId())
                .build();
        transactionRepository.save(transaction);
    }


}

