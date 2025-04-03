package vn.pickleball.courtservice.model.request;

import lombok.Data;

@Data
public class CourtServicePurchaseRequest {
    private String id;           // ID của dịch vụ
    private Integer quantity;  // Số lượng khách mua
}

