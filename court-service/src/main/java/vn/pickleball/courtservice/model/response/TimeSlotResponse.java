package vn.pickleball.courtservice.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class TimeSlotResponse {
    private String id;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal regularPrice;
    private BigDecimal dailyPrice;
    private BigDecimal studentPrice;
}
