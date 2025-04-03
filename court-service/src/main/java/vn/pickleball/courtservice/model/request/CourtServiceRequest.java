package vn.pickleball.courtservice.model.request;

import lombok.Data;

@Data
public class CourtServiceRequest {
    private String id;
    private String courtId;
    private String category;
    private String name;
    private Double price;
    private Integer quantity;
    private String unit;
    private String description;
    private String imageUrl;
    private boolean isActive;
    private Integer soldCount;
}
