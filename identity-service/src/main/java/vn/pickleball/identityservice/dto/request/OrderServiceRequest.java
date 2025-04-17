package vn.pickleball.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderServiceRequest {
    @NotBlank(message = "Court ID is required")
    private String courtId;

    @NotBlank(message = "Court name is required")
    private String courtName;

    @NotBlank(message = "Address is required")
    private String address;

    private String customerName;

    private String phoneNumber;

    private String userId;

    @NotBlank
    private String orderStatus;

    @NotBlank
    private String note;

    private BigDecimal paymentAmount;


    @NotEmpty(message = "Order details cannot be empty")
    private List<ServiceDetailRequest> serviceDetails;
}
