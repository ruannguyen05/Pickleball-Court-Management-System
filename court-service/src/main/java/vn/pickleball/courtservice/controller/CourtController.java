package vn.pickleball.courtservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtDetail;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.PageResponse;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.service.CourtImageService;
import vn.pickleball.courtservice.service.CourtService;
import vn.pickleball.courtservice.service.FirebaseStorageService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;
    private final FirebaseStorageService firebaseStorageService;
    private final CourtRepository courtRepository;

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

    @GetMapping("/getCourtsByManager")
    public ResponseEntity<List<CourtResponse>>  getCourtsByManager() {
        List<CourtResponse> courtResponses = courtService.getCourtsByManagerId();
        return ResponseEntity.ok(courtResponses);
    }

    @GetMapping("/courtDetail/{id}")
    public ResponseEntity<CourtDetail> getCourtDetail(@PathVariable String id) {
        CourtDetail courtResponse = courtService.getCourtDetail(id);
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

    @PutMapping("/disable/{id}")
    public ResponseEntity<Void> disableCourt(@PathVariable String id) {
        courtService.disableCourt(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/active/{id}")
    public ResponseEntity<Void> activeCourt(@PathVariable String id) {
        courtService.activeCourt(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/getAllPageable")
    public PageResponse<CourtResponse> getAllPage(@RequestParam(defaultValue = "0", required = false) int page,
                                                  @RequestParam(defaultValue = "20", required = false) int size,
                                                  @RequestParam(defaultValue = "") String search) {
        return courtService.getAllPageable(page, size, search);
    }

    @PostMapping("/upload-logo")
    public String uploadLogo(@RequestParam String courtId, @RequestParam("file") MultipartFile file) throws IOException {
        Court court = courtRepository.findById(courtId).orElseThrow(() -> new RuntimeException("Court not found"));

        if (court.getLogoUrl() != null) {
            firebaseStorageService.deleteFile(court.getLogoUrl());
        }

        String fileUrl = firebaseStorageService.uploadFile(file, "courts/" + courtId);
        court.setLogoUrl(fileUrl);
        courtRepository.save(court);
        return fileUrl;
    }

    @PostMapping("/upload-background")
    public String uploadBackground(@RequestParam String courtId, @RequestParam("file") MultipartFile file) throws IOException {
        Court court = courtRepository.findById(courtId).orElseThrow(() -> new RuntimeException("Court not found"));

        if (court.getBackgroundUrl() != null) {
            firebaseStorageService.deleteFile(court.getBackgroundUrl());
        }

        String fileUrl = firebaseStorageService.uploadFile(file, "courts/" + courtId);
        court.setBackgroundUrl(fileUrl);
        courtRepository.save(court);
        return fileUrl;
    }
}
