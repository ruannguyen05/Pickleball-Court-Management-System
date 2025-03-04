package vn.pickleball.identityservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.pickleball.identityservice.dto.Gender;
import vn.pickleball.identityservice.dto.UserRank;
import vn.pickleball.identityservice.validator.DobConstraint;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @Size(min = 4, message = "USERNAME_INVALID")
    String username;

    @Size(min = 6, message = "INVALID_PASSWORD")
    String password;

    String firstName;
    String lastName;


    @DobConstraint(min = 10, message = "INVALID_DOB")
    LocalDate dob;

    List<String> roles;
    String email;

    String phoneNumber;

    boolean isStudent;

    UserRank userRank;

    Gender gender;
}
