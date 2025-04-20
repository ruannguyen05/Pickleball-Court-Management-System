package vn.pickleball.courtservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourtRequest {
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String address;

    @NotBlank
    private String phone;
    private String openTime;
    private boolean isActive;
    private String email;
    private String link;
}
