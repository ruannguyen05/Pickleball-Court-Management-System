package vn.pickleball.courtservice.model.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourtMap {
    private String id;
    private String name;
    private String address;
}
