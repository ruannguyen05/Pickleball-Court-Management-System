package vn.pickleball.identityservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.pickleball.identityservice.dto.Gender;
import vn.pickleball.identityservice.dto.UserRank;
import vn.pickleball.identityservice.validator.DobConstraint;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String id;
    String username;

    String firstName;
    String lastName;

    @DobConstraint(min = 18, message = "INVALID_DOB")
    LocalDate dob;

    List<String> roles;

    String email;

    String phoneNumber;

    Gender gender;

    List<String> courtIds;

    private boolean isActive;
}
