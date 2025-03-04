package vn.pickleball.identityservice.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.mapstruct.Named;
import vn.pickleball.identityservice.dto.Gender;
import vn.pickleball.identityservice.dto.UserRank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User extends BaseEntity{

    @Column(name = "username", unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_unicode_ci")
    private String username;

    private String password;

    private String firstName;

    private String lastName;

    private LocalDate dob;

    @Column(name = "email", unique = true, updatable = false)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "is_student")
    private boolean isStudent;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "user_rank")
    @Enumerated(EnumType.STRING)
    private UserRank userRank;

    @ManyToMany
    Set<Role> roles;

    @OneToMany(mappedBy = "user")
    @JsonManagedReference
    private List<Order> orders;
}
