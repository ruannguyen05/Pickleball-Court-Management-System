package vn.pickleball.identityservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OrderPage {
    private List<OrderData> orders;
    private int totalPages;
    private long totalElements;
}
