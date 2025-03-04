package vn.pickleball.identityservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order extends BaseEntity{

    @Column(nullable = false)
    private String courtId;

    @Column(nullable = false)
    private String courtName; // Tên sân (ví dụ: "CLB Pickleball Hoàng Thành Trung")

    @Column(nullable = false)
    private String address; // Địa chỉ sân (ví dụ: "449 Lê Văn Việt quận 9")

    @Column(nullable = false)
    private LocalDate bookingDate; // Ngày đặt sân (ví dụ: "22/02/2025")

    @Column(nullable = false)
    private String customerName; // Tên người đặt (ví dụ: "Nguyễn Văn Ruấn")

    @Column(nullable = false)
    private String phoneNumber; // Số điện thoại (ví dụ: "+84 981604658")

    @Column
    private String note; // Ghi chú (ví dụ: "Nhập ghi chú")

    @Column(nullable = false)
    private String orderType;

    @Column(nullable = false)
    private String orderStatus; // Trạng thái đơn hàng

    @Column(nullable = false)
    private String paymentStatus;

    @Column
    private String totalTime;

    @Column
    private String discountCode; // Mã giảm giá (nếu có)

    @Column
    private BigDecimal totalAmount;

    @Column
    private BigDecimal discountAmount;

    @Column
    private BigDecimal paymentAmount;

    @Column
    private BigDecimal amountPaid; // Số tiền đã thanh toán

    @Column
    private LocalDateTime paymentTimeout;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderDetail> orderDetails; // Danh sách OrderDetail

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Transaction> transactions;
}