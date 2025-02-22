package vn.pickleball.courtservice.model.response;

import lombok.Data;

import java.util.List;

@Data
public class CourtSlotBookingResponse {
    private String courtSlotId; // ID của CourtSlot
    private List<BookingSlotResponse> bookingSlots; // Danh sách BookingSlotResponse
}