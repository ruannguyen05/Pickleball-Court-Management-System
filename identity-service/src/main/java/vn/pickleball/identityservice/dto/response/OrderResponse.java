package vn.pickleball.identityservice.dto.response;

import lombok.Builder;
import lombok.Data;
import vn.pickleball.identityservice.dto.request.OrderDetailRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private String id;
    private String courtId;
    private String courtName;
    private String address;
    private String userId;
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
    private BigDecimal depositAmount;
    private BigDecimal amountPaid;
    private BigDecimal amountRefund;
    private LocalDateTime paymentTimeout;
    private List<OrderDetailResponse> orderDetails;
    private String qrcode;
    private LocalDateTime createdAt;

}
