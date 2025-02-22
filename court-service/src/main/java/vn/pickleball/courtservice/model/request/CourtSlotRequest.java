package vn.pickleball.courtservice.model.request;

import lombok.Data;

@Data
public class CourtSlotRequest {
    private String id;
    private String courtId;
    private String name;
    private boolean isActive;
}
