package vn.pickleball.courtservice.model.response;

import lombok.Data;

@Data
public class CourtSlotResponse {
    private String id;
    private String courtId;
    private String name;
    private boolean isActive;
}

