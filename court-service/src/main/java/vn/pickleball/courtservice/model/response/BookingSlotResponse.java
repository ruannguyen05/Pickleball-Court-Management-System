package vn.pickleball.courtservice.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import vn.pickleball.courtservice.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class BookingSlotResponse {
    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    private LocalTime endTime;

    private BigDecimal regularPrice;

    private BigDecimal dailyPrice;

    private BigDecimal studentPrice;

    private BookingStatus status;
}