package vn.pickleball.courtservice.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;

@Data
@Entity
@Table(name = "court")
public class Court extends BaseEntity{

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    private String openTime;

    private boolean isActive;

    private String email;

    private String link;

    private String ownerId;

    @OneToMany(mappedBy = "court")
    @JsonManagedReference
    private List<CourtSlot> courtSlots;

    // 1 Business có nhiều TimeSlot được cấu hình
    @OneToMany(mappedBy = "court")
    @JsonManagedReference
    private List<TimeSlot> timeSlots;

    // 1 Business có nhiều giá cho từng khung giờ và loại khách hàng
    @OneToMany(mappedBy = "court")
    @JsonManagedReference
    private List<CourtPrice> courtPrices;
}
