package vn.pickleball.courtservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import vn.pickleball.courtservice.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "booking_slot")
public class BookingSlot {
    @Id
    @UuidGenerator
    @Column(name = "id", length = 36)
    private String id;

    @Column(nullable = false)
    private LocalDate dateBooking; // Ngày đặt sân

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private BigDecimal price; // Giá theo khung giờ

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status; // Trạng thái sân

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "court_slot_id", nullable = false)
    private CourtSlot courtSlot; // Sân áp dụng
}
