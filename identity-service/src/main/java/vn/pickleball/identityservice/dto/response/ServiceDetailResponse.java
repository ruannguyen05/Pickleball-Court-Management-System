package vn.pickleball.identityservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ServiceDetailRequest {
    private String courtServiceId;
    private String courtServiceName;
    private int quantity;
    private BigDecimal price;
}
