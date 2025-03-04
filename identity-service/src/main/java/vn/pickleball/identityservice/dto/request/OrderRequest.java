package vn.pickleball.identityservice.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OrderRequest {
    private String courtId;
    private String courtName;
    private String address;
    private LocalDate bookingDate;
    private String customerName;
    private String phoneNumber;
    private String note;
    private String orderType;
    private String discountCode;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paymentAmount;
    private BigDecimal amountPaid;
    private String paymentStatus;
    private List<OrderDetailRequest> orderDetails;
}
