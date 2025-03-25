package vn.pickleball.courtservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.courtservice.entity.CourtImage;
import vn.pickleball.courtservice.model.request.CourtSlotRequest;
import vn.pickleball.courtservice.model.request.UpdateBookingSlot;
import vn.pickleball.courtservice.model.response.CourtPriceResponse;
import vn.pickleball.courtservice.model.response.CourtSlotBookingResponse;
import vn.pickleball.courtservice.model.response.CourtSlotResponse;
import vn.pickleball.courtservice.service.BookingSlotService;
import vn.pickleball.courtservice.service.CourtImageService;
import vn.pickleball.courtservice.service.CourtPriceService;
import vn.pickleball.courtservice.service.CourtSlotService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {
    private final CourtSlotService courtSlotService;
    private final CourtPriceService courtPriceService;
    private final BookingSlotService bookingSlotService;
    private final CourtImageService courtImageService;

    @GetMapping("/court_slot/{id}")
    public ResponseEntity<CourtSlotResponse> getCourtSlotById(@PathVariable String id) {
        CourtSlotResponse courtSlotResponse = courtSlotService.getCourtSlotById(id);
        return ResponseEntity.ok(courtSlotResponse);
    }

    @GetMapping("/court_slot")
    public ResponseEntity<List<CourtSlotResponse>> getAllCourtSlots() {
        List<CourtSlotResponse> courtSlotResponses = courtSlotService.getAllCourtSlots();
        return ResponseEntity.ok(courtSlotResponses);
    }

    @GetMapping("/court_slot/getByCourtId/{courtId}")
    public ResponseEntity<List<CourtSlotResponse>> getCourtSlotsByCourtId(@PathVariable String courtId) {
        List<CourtSlotResponse> courtSlotResponses = courtSlotService.getCourtSlotsByCourtId(courtId);
        return ResponseEntity.ok(courtSlotResponses);
    }

    @GetMapping("/court_price/getByCourtId/{courtId}")
    public ResponseEntity<CourtPriceResponse> getCourtPriceByCourtId(@PathVariable String courtId) {
        CourtPriceResponse courtPriceResponse = courtPriceService.getCourtPriceByCourtId(courtId);
        return ResponseEntity.ok(courtPriceResponse);
    }

    @GetMapping("/booking_slot")
    public ResponseEntity<List<CourtSlotBookingResponse>> getBookingSlots(
            @RequestParam String courtId,
            @RequestParam LocalDate dateBooking
    ) {
        List<CourtSlotBookingResponse> bookingSlots = bookingSlotService.getBookingSlots(courtId, dateBooking);
        return ResponseEntity.ok(bookingSlots);
    }

    @PostMapping("/booking_slot/update")
    public ResponseEntity<Void> updateBookingSlots(
            @RequestBody UpdateBookingSlot updateBookingSlot
            ) {
        bookingSlotService.updateBookingSlotsInRedis(updateBookingSlot.getCourtId(),updateBookingSlot.getDateBooking(), updateBookingSlot.getStatus(),updateBookingSlot.getCourtSlotBookings());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/court-images/list")
    public ResponseEntity<List<CourtImage>> getCourtImages(
            @RequestParam("courtId") String courtId,
            @RequestParam("isMap") boolean isMap) {
        return ResponseEntity.ok(courtImageService.getCourtImages(courtId, isMap));
    }
}
