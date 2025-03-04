package vn.pickleball.identityservice.dto.response;

import lombok.Data;
import vn.pickleball.identityservice.dto.request.OrderDetailRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private String id;
    private String courtName;
    private String address;
    private LocalDate bookingDate;
    private String customerName;
    private String phoneNumber;
    private String note;
    private String orderType;
    private String orderStatus;
    private String paymentStatus;
    private String discountCode;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paymentAmount;
    private BigDecimal amountPaid;
    private LocalDateTime paymentTimeout;
    private List<OrderDetailRequest> orderDetails;
    private String qrcode;
}
