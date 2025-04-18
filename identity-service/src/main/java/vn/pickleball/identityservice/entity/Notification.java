package vn.pickleball.identityservice.entity;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    private String title;

    private String description;

    private String status;

    private LocalDateTime createAt;

    @Column(columnDefinition = "TEXT")
    private String notificationData;

    @Column(name = "phone_number")
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "phone_number", referencedColumnName = "phone_number",
            insertable = false, updatable = false,foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT) )
    @JsonBackReference
    private User user;
}
