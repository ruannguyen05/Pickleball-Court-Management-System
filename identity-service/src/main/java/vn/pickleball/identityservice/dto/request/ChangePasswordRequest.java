package vn.pickleball.identityservice.dto.request;


import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String password;
    private String newPassword;
}