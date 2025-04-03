package vn.pickleball.courtservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtImage;
import vn.pickleball.courtservice.model.request.BookingPaymentRequest;
import vn.pickleball.courtservice.model.request.CourtServicePurchaseRequest;
import vn.pickleball.courtservice.model.request.CourtSlotRequest;
import vn.pickleball.courtservice.model.request.UpdateBookingSlot;
import vn.pickleball.courtservice.model.response.CourtPriceResponse;
import vn.pickleball.courtservice.model.response.CourtServiceResponse;
import vn.pickleball.courtservice.model.response.CourtSlotBookingResponse;
import vn.pickleball.courtservice.model.response.CourtSlotResponse;
import vn.pickleball.courtservice.service.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
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
    private final CourtService_Service courtServiceService;
    private final CourtMaintenanceHistoryService maintenanceHistoryService;
    private final FirebaseStorageService firebaseStorageService;

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

    @GetMapping("/court_slot/getIdsByCourtId/{courtId}")
    public ResponseEntity<List<String>> getCourtSlotIdsByCourtId(@PathVariable String courtId) {
        List<String> courtSlotIds = courtSlotService.getCourtSlotsByCourtId(courtId)
                .stream()
                .map(CourtSlotResponse::getName)
                .toList();
        return ResponseEntity.ok(courtSlotIds);
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

    @GetMapping("/getServices")
    public ResponseEntity<List<CourtServiceResponse>> getByCourtId(@RequestParam String  courtId) {
        return ResponseEntity.ok(courtServiceService.getCourtServicesByCourtId(courtId));
    }

    @PostMapping("/service/purchase")
    public ResponseEntity<?> purchaseUpdate(@RequestBody List<CourtServicePurchaseRequest> requests) {
        courtServiceService.updateAfterPurchase(requests);
        return ResponseEntity.ok("Purchase update successful");
    }


    @GetMapping("/check-maintenance-slots")
    public ResponseEntity<Map<String, List<LocalDate>>> getInvalidCourtSlots(
            @RequestParam String courtId,
            @RequestParam List<LocalDate> bookingDates,
            @RequestParam LocalTime startTime,
            @RequestParam LocalTime endTime) {

        Map<String, List<LocalDate>> invalidCourtSlots = maintenanceHistoryService.getMaintenanceCourtSlots(
                courtId, bookingDates, startTime, endTime);
        return ResponseEntity.ok(invalidCourtSlots);
    }

    @GetMapping("/court_slot/id-by-name")
    public String getCourtSlotIdByName(
            @RequestParam String courtId,
            @RequestParam String name) {
        CourtSlotResponse response = courtSlotService.getByName(courtId, name);
        return response.getName();
    }

    @PostMapping("/calculate-total-payment")
    public BigDecimal calculateTotalPayment(@RequestBody BookingPaymentRequest request) {
        return courtPriceService.calculateTotalPayment(request);
    }

    @PostMapping("/upload-avatar")
    public String uploadAvatar(@RequestPart("file") MultipartFile file,
                               @RequestPart(value = "oldPath", required = false) String oldPath) throws IOException {

        if (oldPath != null) {
            firebaseStorageService.deleteFile(oldPath);
        }

        return firebaseStorageService.uploadFile(file, "avatars");
    }
    @PostMapping("/synchronous")
    public void synchronous(@RequestParam String courtId){
        bookingSlotService.deleteBookingSlotsByCourtId(courtId);
    }
}
