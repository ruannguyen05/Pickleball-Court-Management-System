package vn.pickleball.courtservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.courtservice.model.request.CourtSlotRequest;
import vn.pickleball.courtservice.model.response.CourtSlotResponse;
import vn.pickleball.courtservice.service.CourtSlotService;

import java.util.List;

@RestController
@RequestMapping("/court_slot")
@RequiredArgsConstructor
public class CourtSlotController {
    private final CourtSlotService courtSlotService;

    @PostMapping
    public ResponseEntity<CourtSlotResponse> createCourtSlot(@RequestBody CourtSlotRequest courtSlotRequest) {
        CourtSlotResponse courtSlotResponse = courtSlotService.createCourtSlot(courtSlotRequest);
        return ResponseEntity.ok(courtSlotResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourtSlotResponse> getCourtSlotById(@PathVariable String id) {
        CourtSlotResponse courtSlotResponse = courtSlotService.getCourtSlotById(id);
        return ResponseEntity.ok(courtSlotResponse);
    }

    @GetMapping
    public ResponseEntity<List<CourtSlotResponse>> getAllCourtSlots() {
        List<CourtSlotResponse> courtSlotResponses = courtSlotService.getAllCourtSlots();
        return ResponseEntity.ok(courtSlotResponses);
    }

    @PutMapping()
    public ResponseEntity<CourtSlotResponse> updateCourtSlot(@RequestBody CourtSlotRequest courtSlotRequest) {
        CourtSlotResponse courtSlotResponse = courtSlotService.updateCourtSlot( courtSlotRequest);
        return ResponseEntity.ok(courtSlotResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourtSlot(@PathVariable String id) {
        courtSlotService.deleteCourtSlot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/court/{courtId}")
    public ResponseEntity<List<CourtSlotResponse>> getCourtSlotsByCourtId(@PathVariable String courtId) {
        List<CourtSlotResponse> courtSlotResponses = courtSlotService.getCourtSlotsByCourtId(courtId);
        return ResponseEntity.ok(courtSlotResponses);
    }
}
