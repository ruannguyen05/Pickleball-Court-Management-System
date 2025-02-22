package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtPrice;
import vn.pickleball.courtservice.entity.TimeSlot;
import vn.pickleball.courtservice.mapper.CourtPriceMapper;
import vn.pickleball.courtservice.mapper.TimeSlotMapper;
import vn.pickleball.courtservice.model.WeekType;
import vn.pickleball.courtservice.model.request.CourtPriceRequest;
import vn.pickleball.courtservice.model.request.TimeSlotRequest;
import vn.pickleball.courtservice.model.response.CourtPriceResponse;
import vn.pickleball.courtservice.model.response.TimeSlotResponse;
import vn.pickleball.courtservice.repository.CourtPriceRepository;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.TimeSlotRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtPriceService {

    private final CourtPriceRepository courtPriceRepository;

    private final TimeSlotRepository timeSlotRepository;

    private final CourtRepository courtRepository;

    private final CourtPriceMapper courtPriceMapper;

    private final TimeSlotMapper timeSlotMapper;

    public CourtPriceResponse createOrUpdateCourtPrice(CourtPriceRequest courtPriceRequest) {
        Court court = courtRepository.findById(courtPriceRequest.getCourtId())
                .orElseThrow(() -> new RuntimeException("Court not found"));
        if(courtPriceRequest.getWeekendTimeSlots()!= null) {
            // Xử lý WEEKDAY
            List<TimeSlot> updatedWeekdayTimeSlots = processTimeSlots(
                    courtPriceRequest.getWeekdayTimeSlots(),
                    court,
                    WeekType.WEEKDAY
            );

            // Xử lý WEEKEND
            List<TimeSlot> updatedWeekendTimeSlots = processTimeSlots(
                    courtPriceRequest.getWeekendTimeSlots(),
                    court,
                    WeekType.WEEKEND
            );

            // Tạo response
            CourtPriceResponse response = new CourtPriceResponse();
            response.setCourtId(court.getId());
            response.setWeekdayTimeSlots(timeSlotMapper.toTimeSlotResponseList(updatedWeekdayTimeSlots));
            response.setWeekendTimeSlots(timeSlotMapper.toTimeSlotResponseList(updatedWeekendTimeSlots));

            return response;
        }else{
            List<TimeSlot> updatedWeekdayTimeSlots = processTimeSlots(
                    courtPriceRequest.getWeekdayTimeSlots(),
                    court,
                    WeekType.NONE
            );
            CourtPriceResponse response = new CourtPriceResponse();
            response.setCourtId(court.getId());
            response.setWeekdayTimeSlots(timeSlotMapper.toTimeSlotResponseList(updatedWeekdayTimeSlots));

            return response;
        }


    }

    private List<TimeSlot> processTimeSlots(
            List<TimeSlotRequest> timeSlotRequests,
            Court court,
            WeekType weekType
    ) {
        if (timeSlotRequests == null || timeSlotRequests.isEmpty()) {
            return List.of();
        }

        List<TimeSlot> updatedTimeSlots = new ArrayList<>();

        for (TimeSlotRequest timeSlotRequest : timeSlotRequests) {
            TimeSlot timeSlot;
            if (timeSlotRequest.getId() != null) {
                // Cập nhật timeSlot hiện có
                timeSlot = timeSlotRepository.findById(timeSlotRequest.getId())
                        .orElseThrow(() -> new RuntimeException("TimeSlot not found: " + timeSlotRequest.getId()));
                timeSlot.setStartTime(timeSlotRequest.getStartTime());
                timeSlot.setEndTime(timeSlotRequest.getEndTime());
            } else {
                // Thêm mới timeSlot
                timeSlot = timeSlotMapper.timeSlotRequestToTimeSlot(timeSlotRequest);
                timeSlot.setCourt(court);
            }

            // Tạo hoặc cập nhật CourtPrice cho TimeSlot
            CourtPrice courtPrice = timeSlot.getCourtPrice();
            if (courtPrice == null) {
                courtPrice = new CourtPrice();
                courtPrice.setTimeSlot(timeSlot);
                courtPrice.setCourt(court);
                courtPrice.setWeekType(weekType);
            }
            courtPrice.setRegularPrice(timeSlotRequest.getRegularPrice());
            courtPrice.setDailyPrice(timeSlotRequest.getDailyPrice());
            courtPrice.setStudentPrice(timeSlotRequest.getStudentPrice());

            timeSlot.setCourtPrice(courtPrice);
            updatedTimeSlots.add(timeSlot);
        }

        // Kiểm tra trùng lặp
        validateTimeSlots(updatedTimeSlots);

        // Lưu các timeSlot và courtPrice
        return timeSlotRepository.saveAll(updatedTimeSlots);
    }

    private void validateTimeSlots(List<TimeSlot> timeSlots) {
        for (int i = 0; i < timeSlots.size(); i++) {
            TimeSlot current = timeSlots.get(i);

            if (current.getStartTime().isAfter(current.getEndTime())) {
                throw new RuntimeException("Start time must be before end time for time slot: " + current.getId());
            }

            for (int j = i + 1; j < timeSlots.size(); j++) {
                TimeSlot next = timeSlots.get(j);

                if (current.getStartTime().isBefore(next.getEndTime()) &&
                        current.getEndTime().isAfter(next.getStartTime())) {
                    throw new RuntimeException("Time slots overlap: " + current.getId() + " and " + next.getId());
                }
            }
        }
    }

    public CourtPriceResponse getCourtPriceByCourtId(String courtId) {
        List<TimeSlot> timeSlots = timeSlotRepository.findByCourtId(courtId);

        CourtPriceResponse response = new CourtPriceResponse();
        response.setCourtId(courtId);

        // Phân loại timeSlot theo weekType
        List<TimeSlotResponse> weekdayTimeSlots = new ArrayList<>();
        List<TimeSlotResponse> weekendTimeSlots = new ArrayList<>();

        for (TimeSlot timeSlot : timeSlots) {
            TimeSlotResponse timeSlotResponse = timeSlotMapper.timeSlotToTimeSlotResponse(timeSlot);
            if (timeSlot.getCourtPrice().getWeekType() == WeekType.WEEKDAY) {
                weekdayTimeSlots.add(timeSlotResponse);
            } else {
                weekendTimeSlots.add(timeSlotResponse);
            }
        }

        response.setWeekdayTimeSlots(weekdayTimeSlots);
        response.setWeekendTimeSlots(weekendTimeSlots);

        return response;
    }
}
