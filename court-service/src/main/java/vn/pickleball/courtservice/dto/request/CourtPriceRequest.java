package vn.pickleball.courtservice.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CourtPriceRequest {
    private String courtId;
    private List<TimeSlotRequest> weekdayTimeSlots; // Khung thời gian cho WEEKDAY
    private List<TimeSlotRequest> weekendTimeSlots; // Khung thời gian cho WEEKEND
}
