package vn.pickleball.courtservice.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class TimeSlotRequest {
    private String id; // ID của timeSlot (nếu có)
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal regularPrice;
    private BigDecimal dailyPrice;
    private BigDecimal studentPrice;
}