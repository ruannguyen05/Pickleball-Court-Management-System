package vn.pickleball.identityservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDto {
    private String courtSlotId;
    private String courtSlotName;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
}
