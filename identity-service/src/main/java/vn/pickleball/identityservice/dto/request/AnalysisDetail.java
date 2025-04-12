package vn.pickleball.identityservice.dto.request;

import lombok.Data;

@Data
public class AnalysisDetail {
    private String dayOfWeek;

    private Integer totalSlots;

    private Integer bookedSlots;

    private Double occupancyRate;
}
