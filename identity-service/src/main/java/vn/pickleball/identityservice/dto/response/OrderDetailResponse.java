package vn.pickleball.identityservice.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class OrderDetailResponse {
    private String courtSlotName;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
}
