package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.courtservice.Utils.SecurityContextUtil;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.mapper.CourtMapper;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtDetail;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.PageResponse;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.pagination.PaginationCriteria;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CourtService {

    private final CourtRepository courtRepository;

    private final CourtMapper courtMapper;

    private final PaginationCriteria paginationCriteria;

    private final BookingSlotService bookingSlotService;

    private final FirebaseStorageService firebaseStorageService;

    // Create
    @PreAuthorize("hasRole('ADMIN')")
    public CourtResponse createCourt(CourtRequest courtRequest) {
        Court court = courtMapper.courtRequestToCourt(courtRequest);
        court.setActive(true);
        Court savedCourt = courtRepository.save(court);
        try {

            if (courtRequest.getLogoUrl() != null && !courtRequest.getLogoUrl().isEmpty()) {
                String logoPath = null;

                logoPath = firebaseStorageService.uploadFile(courtRequest.getLogoUrl(), "courts/" + savedCourt.getId() + "/logo");

                savedCourt.setLogoUrl(logoPath);
            }

            if (courtRequest.getBackgroundUrl() != null && !courtRequest.getBackgroundUrl().isEmpty()) {
                String bgPath = firebaseStorageService.uploadFile(courtRequest.getBackgroundUrl(), "courts/" + savedCourt.getId() + "/background");
                savedCourt.setBackgroundUrl(bgPath);
            }

            Court updatedCourt = courtRepository.save(savedCourt);
            return courtMapper.courtToCourtResponse(updatedCourt);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    // Read (Get All)
    public List<CourtResponse> getAllCourts() {
        List<Court> courts = courtRepository.findAll();
        return courts.stream()
                .map(courtMapper::courtToCourtResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<CourtResponse> getCourtsByManagerId() {
        String managerId = SecurityContextUtil.getUid();
        List<Court> courts = courtRepository.findByManagerId(managerId);
        return courts.stream()
                .map(courtMapper::courtToCourtResponse)
                .collect(Collectors.toList());
    }


    public CourtResponse getCourtById(String id) {
        Optional<Court> court = courtRepository.findById(id);
        return court.map(courtMapper::courtToCourtResponse).orElse(null);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public CourtDetail getCourtDetail(String id) {
        Optional<Court> court = courtRepository.findById(id);
        return court.map(courtMapper::courtToCourtDetail).orElse(null);
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public CourtResponse updateCourt(CourtRequest courtRequest) {
        Court existingCourt = courtRepository.findById(courtRequest.getId())
                .orElseThrow(() -> new RuntimeException("Court not found"));

        try {

            courtMapper.updateCourt(existingCourt, courtRequest);

            if (courtRequest.getLogoUrl() != null && !courtRequest.getLogoUrl().isEmpty()) {
                if (existingCourt.getLogoUrl() != null) {
                    firebaseStorageService.deleteFile(existingCourt.getLogoUrl());
                }
                String newLogoPath = firebaseStorageService.uploadFile(courtRequest.getLogoUrl(), "courts/" + existingCourt.getId() + "/logo");
                existingCourt.setLogoUrl(newLogoPath);
            }

            if (courtRequest.getBackgroundUrl() != null && !courtRequest.getBackgroundUrl().isEmpty()) {
                if (existingCourt.getBackgroundUrl() != null) {
                    firebaseStorageService.deleteFile(existingCourt.getBackgroundUrl());
                }
                String newBgPath = firebaseStorageService.uploadFile(courtRequest.getBackgroundUrl(), "courts/" + existingCourt.getId() + "/background");
                existingCourt.setBackgroundUrl(newBgPath);
            }

            Court updatedCourt = courtRepository.save(existingCourt);
            bookingSlotService.deleteBookingSlotsByCourtId(updatedCourt.getId());
            return courtMapper.courtToCourtResponse(updatedCourt);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @PreAuthorize("hasRole('ADMIN')")
    // Delete
    public void deleteCourt(String id) {
        bookingSlotService.deleteBookingSlotsByCourtId(id);
        courtRepository.deleteById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public void disableCourt(String id) {
        Optional<Court> court = courtRepository.findById(id);
        if (court.isPresent()) {
            Court existingCourt = court.get();
            existingCourt.setActive(false);
            Court updatedCourt = courtRepository.save(existingCourt);
            bookingSlotService.deleteBookingSlotsByCourtId(updatedCourt.getId());
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public void activeCourt(String id) {
        Optional<Court> court = courtRepository.findById(id);
        if (court.isPresent()) {
            Court existingCourt = court.get();
            existingCourt.setActive(true);
            Court updatedCourt = courtRepository.save(existingCourt);
            bookingSlotService.deleteBookingSlotsByCourtId(updatedCourt.getId());
        }
    }

    public PageResponse<CourtResponse> getAllPageable(int page, int pageSize, String search) {
        String tenantId = SecurityContextUtil.getUid();

        int offset = Math.max(0, (page - 1) * pageSize);

        List<Court> courts = paginationCriteria.getCourts(offset, pageSize, search);
        Long totalElements = paginationCriteria.getTotalCourts(search);

        List<CourtResponse> responses = courtMapper.courtsToCourtResponses(courts);

        return PageResponse.<CourtResponse>builder()
                .page(page)
                .size(pageSize)
                .totalElements(totalElements)
                .data(responses)
                .build();
    }
}
