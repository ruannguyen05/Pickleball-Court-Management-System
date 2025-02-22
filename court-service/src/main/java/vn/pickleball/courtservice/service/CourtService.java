package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.courtservice.Utils.SecurityContextUtil;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.mapper.CourtMapper;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.PageResponse;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.pagination.PaginationCriteria;

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

    // Create
    public CourtResponse createCourt(CourtRequest courtRequest) {
        String uId = SecurityContextUtil.getUid();
        Court court = courtMapper.courtRequestToCourt(courtRequest);
        court.setOwnerId(uId);
        court.setActive(true);
        Court savedCourt = courtRepository.save(court);
        return courtMapper.courtToCourtResponse(savedCourt);
    }

    // Read (Get All)
    public List<CourtResponse> getAllCourts() {
        List<Court> courts = courtRepository.findAll();
        return courts.stream()
                .map(courtMapper::courtToCourtResponse)
                .collect(Collectors.toList());
    }

    // Read (Get By Id)
    public CourtResponse getCourtById(String id) {
        Optional<Court> court = courtRepository.findById(id);
        return court.map(courtMapper::courtToCourtResponse).orElse(null);
    }

    // Update
    public CourtResponse updateCourt( CourtRequest courtRequest) {
        Optional<Court> optionalCourt = courtRepository.findById(courtRequest.getId());
        if (optionalCourt.isPresent()) {
            Court existingCourt = optionalCourt.get();
            courtMapper.updateCourt(existingCourt, courtRequest);
            Court updatedCourt = courtRepository.save(existingCourt);
            return courtMapper.courtToCourtResponse(updatedCourt);
        }
        return null;
    }

    // Delete
    public void deleteCourt(String id) {
        courtRepository.deleteById(id);
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
