package vn.pickleball.courtservice.model.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

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
    private String managerId;
}
