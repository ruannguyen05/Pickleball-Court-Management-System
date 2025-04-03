package vn.pickleball.identityservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import vn.pickleball.identityservice.dto.request.BookingPaymentRequest;
import vn.pickleball.identityservice.dto.request.UpdateBookingSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@FeignClient(name = "court-client", url = "${court_service.url_service}")
public interface CourtClient {

    @PostMapping("${court_service.update_bookingSlot}")
    ResponseEntity<Void> updateBookingSlot(@RequestBody UpdateBookingSlot updateBookingSlot);

    @GetMapping("/public/check-maintenance-slots")
    ResponseEntity<Map<String, List<LocalDate>>> getInvalidCourtSlots(
            @RequestParam("courtId") String courtId,
            @RequestParam("bookingDates") List<LocalDate> bookingDates,
            @RequestParam("startTime") LocalTime startTime,
            @RequestParam("endTime") LocalTime endTime);


    @GetMapping("/public/court_slot/getIdsByCourtId/{courtId}")
    ResponseEntity<List<String>> getCourtSlotIdsByCourtId(@PathVariable("courtId") String courtId);

    @GetMapping("/public/court_slot/id-by-name")
    String getCourtSlotIdByName(
            @RequestParam String courtId,
            @RequestParam String name);

    @PostMapping("/public/calculate-total-payment")
    BigDecimal calculateTotalPayment(@RequestBody BookingPaymentRequest request);

    @PostMapping("/public/upload-avatar")
    String uploadAvatar(@RequestParam("file") MultipartFile file, @RequestParam String oldPath);

    @PostMapping("/public/synchronous")
    void synchronous(@RequestParam String courtId);
}

