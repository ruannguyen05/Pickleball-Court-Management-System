package vn.pickleball.courtservice.model.request;

import lombok.Data;

@Data
public class CourtRequest {
    private String id;
    private String name;
    private String address;
    private String phone;
    private String openTime;
    private boolean isActive;
    private String email;
    private String link;
}
