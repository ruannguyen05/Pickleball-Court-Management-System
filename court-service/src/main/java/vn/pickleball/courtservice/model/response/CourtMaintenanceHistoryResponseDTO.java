package vn.pickleball.courtservice.model.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CourtMaintenanceHistoryResponseDTO {
    private String id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime finishAt;
    private String description;
}

