package vn.pickleball.identityservice.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class OrderDetailRequest {
    private String courtSlotId;
    private String courtSlotName;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
}
