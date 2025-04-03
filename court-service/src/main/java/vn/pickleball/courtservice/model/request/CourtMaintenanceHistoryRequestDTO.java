package vn.pickleball.courtservice.model.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CourtMaintenanceHistoryRequestDTO {
    private String id;
    private String courtSlotId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime finishAt;
    private String description;
    private String status;
}

