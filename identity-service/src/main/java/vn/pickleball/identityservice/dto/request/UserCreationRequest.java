package vn.pickleball.identityservice.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.pickleball.identityservice.dto.Gender;
import vn.pickleball.identityservice.dto.UserRank;
import vn.pickleball.identityservice.validator.DobConstraint;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    @Size(min = 4, message = "USERNAME_INVALID")
    String username;

    @Size(min = 6, message = "INVALID_PASSWORD")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$", message = "INVALID_PASSWORD_FORMAT")
    String password;

    String firstName;
    String lastName;


    @DobConstraint(min = 10, message = "INVALID_DOB")
    LocalDate dob;

    List<String> roles;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^(0[0-9]{9,10})$", message = "Phone number must start with 0 and be 10-11 digits long")
    private String phoneNumber;

    boolean isStudent;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    UserRank userRank;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Gender gender;

    private String courtId;

}
