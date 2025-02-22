package vn.pickleball.courtservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.PageResponse;
import vn.pickleball.courtservice.service.CourtService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // Create
    @PostMapping("/create_court")
    public ResponseEntity<CourtResponse> createCourt(@RequestBody CourtRequest courtRequest) {
        CourtResponse courtResponse = courtService.createCourt(courtRequest);
        return ResponseEntity.ok(courtResponse);
    }

    // Read (Get All)
    @GetMapping("/public/getAll")
    public ResponseEntity<List<CourtResponse>> getAllCourts() {
        List<CourtResponse> courtResponses = courtService.getAllCourts();
        return ResponseEntity.ok(courtResponses);
    }

    // Read (Get By Id)
    @GetMapping("/public/{id}")
    public ResponseEntity<CourtResponse> getCourtById(@PathVariable String id) {
        CourtResponse courtResponse = courtService.getCourtById(id);
        if (courtResponse != null) {
            return ResponseEntity.ok(courtResponse);
        }
        return ResponseEntity.notFound().build();
    }

    // Update
    @PutMapping("/update")
    public ResponseEntity<CourtResponse> updateCourt(@RequestBody CourtRequest courtRequest) {
        CourtResponse updatedCourtResponse = courtService.updateCourt( courtRequest);
        if (updatedCourtResponse != null) {
            return ResponseEntity.ok(updatedCourtResponse);
        }
        return ResponseEntity.notFound().build();
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourt(@PathVariable String id) {
        courtService.deleteCourt(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/getAllPageable")
    public PageResponse<CourtResponse> getAllPage(@RequestParam(defaultValue = "0", required = false) int page,
                                                  @RequestParam(defaultValue = "20", required = false) int size,
                                                  @RequestParam(defaultValue = "") String search) {
        return courtService.getAllPageable(page, size, search);
    }
}
