package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtSlot;
import vn.pickleball.courtservice.mapper.CourtSlotMapper;
import vn.pickleball.courtservice.model.request.CourtSlotRequest;
import vn.pickleball.courtservice.model.response.CourtSlotResponse;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.CourtSlotRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CourtSlotService {

    private final CourtSlotRepository courtSlotRepository;

    private final CourtRepository courtRepository;

    private final CourtSlotMapper courtSlotMapper;

    public CourtSlotResponse createCourtSlot(CourtSlotRequest courtSlotRequest) {
        Court court = courtRepository.findById(courtSlotRequest.getCourtId())
                .orElseThrow(() -> new RuntimeException("Court not found"));

        CourtSlot courtSlot = courtSlotMapper.courtSlotRequestToCourtSlot(courtSlotRequest);
        courtSlot.setCourt(court);
        courtSlot.setActive(true);
        courtSlot = courtSlotRepository.save(courtSlot);
        return courtSlotMapper.courtSlotToCourtSlotResponse(courtSlot);
    }

    public CourtSlotResponse getCourtSlotById(String id) {
        CourtSlot courtSlot = courtSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("CourtSlot not found"));
        return courtSlotMapper.courtSlotToCourtSlotResponse(courtSlot);
    }

    public List<CourtSlotResponse> getAllCourtSlots() {
        List<CourtSlot> courtSlots = courtSlotRepository.findAll();
        return courtSlots.stream()
                .map(courtSlotMapper::courtSlotToCourtSlotResponse)
                .collect(Collectors.toList());
    }

    public CourtSlotResponse updateCourtSlot(CourtSlotRequest courtSlotRequest) {
        CourtSlot courtSlot = courtSlotRepository.findById(courtSlotRequest.getId())
                .orElseThrow(() -> new RuntimeException("CourtSlot not found"));

        courtSlot.setName(courtSlotRequest.getName());
        courtSlot.setActive(courtSlotRequest.isActive());

        courtSlot = courtSlotRepository.save(courtSlot);
        return courtSlotMapper.courtSlotToCourtSlotResponse(courtSlot);
    }

    public void deleteCourtSlot(String id) {
        courtSlotRepository.deleteById(id);
    }

    public List<CourtSlotResponse> getCourtSlotsByCourtId(String courtId) {
        List<CourtSlot> courtSlots = courtSlotRepository.findByCourtId(courtId);
        return courtSlots.stream()
                .map(courtSlotMapper::courtSlotToCourtSlotResponse)
                .collect(Collectors.toList());
    }
}
