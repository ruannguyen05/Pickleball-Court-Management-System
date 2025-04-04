package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtServiceEntity;
import vn.pickleball.courtservice.mapper.CourtServiceMapper;
import vn.pickleball.courtservice.model.request.CourtServicePurchaseRequest;
import vn.pickleball.courtservice.model.request.CourtServiceRequest;
import vn.pickleball.courtservice.model.response.CourtServiceResponse;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.CourtServiceRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtService_Service {
    private final CourtRepository courtRepository;
    private final CourtServiceRepository courtServiceRepository;
    private final CourtServiceMapper courtServiceMapper;

    public CourtServiceResponse createCourtService(CourtServiceRequest request) {
        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new RuntimeException("Court not found"));

        CourtServiceEntity courtService = courtServiceMapper.toEntity(request);
        courtService.setCourt(court);
        courtService.setActive(request.isActive());
        courtService.setSoldCount(0);

        return courtServiceMapper.toResponse(courtServiceRepository.save(courtService));
    }

    public CourtServiceResponse updateCourtService(CourtServiceRequest request) {
        CourtServiceEntity courtService = courtServiceRepository.findById(request.getId())
                .orElseThrow(() -> new RuntimeException("CourtService not found"));

        courtServiceMapper.updateEntityFromRequest(request, courtService);
        return courtServiceMapper.toResponse(courtServiceRepository.save(courtService));
    }

    public void activateCourtService(String id, boolean active) {
        CourtServiceEntity courtService = courtServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("CourtService not found"));
        courtService.setActive(active);
        courtServiceRepository.save(courtService);
    }

    public List<CourtServiceResponse> getCourtServicesByCourtId(String courtId) {
        return courtServiceRepository.findByCourtId(courtId)
                .stream()
                .map(courtServiceMapper::toResponse)
                .collect(Collectors.toList());
    }

    public void updateAfterPurchase(List<CourtServicePurchaseRequest> requests) {
        requests.forEach(req -> {
            CourtServiceEntity courtService = courtServiceRepository.findById(req.getId())
                    .orElseThrow(() -> new RuntimeException("CourtService not found with id: " + req.getId()));

            if (req.getQuantity() > courtService.getQuantity()) {
                throw new RuntimeException("Not enough stock for service id: " + req.getId());
            }

            // Trừ quantity
            courtService.setQuantity(courtService.getQuantity() - req.getQuantity());
            // Cộng soldCount
            courtService.setSoldCount(courtService.getSoldCount() + req.getQuantity());

            courtServiceRepository.save(courtService);
        });
    }

}
