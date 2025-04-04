package vn.pickleball.identityservice.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class OrderDetailRequest {
    @NotNull(message = "Booking date is required")
    @FutureOrPresent(message = "Booking date must be today or in the future")
    private LocalDate bookingDate;

    private List<OrderDetailDto> bookingSlots;
}
