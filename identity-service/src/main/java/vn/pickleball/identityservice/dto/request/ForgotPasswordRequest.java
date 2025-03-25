package vn.pickleball.identityservice.dto.request;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String key;
}
