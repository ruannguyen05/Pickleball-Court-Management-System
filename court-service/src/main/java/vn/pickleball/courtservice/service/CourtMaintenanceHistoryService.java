package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import vn.pickleball.courtservice.entity.CourtMaintenanceHistory;
import vn.pickleball.courtservice.entity.CourtSlot;
import vn.pickleball.courtservice.exception.ApiException;
import vn.pickleball.courtservice.model.request.CourtMaintenanceHistoryRequestDTO;
import vn.pickleball.courtservice.model.request.UpdateBookingSlot;
import vn.pickleball.courtservice.model.response.CourtMaintenanceHistoryResponseDTO;
import vn.pickleball.courtservice.repository.CourtMaintenanceHistoryRepository;
import vn.pickleball.courtservice.repository.CourtSlotRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtMaintenanceHistoryService {

    private final CourtMaintenanceHistoryRepository maintenanceHistoryRepository;
    private final CourtSlotRepository courtSlotRepository;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisString;

    public void addMaintenanceHistory(CourtMaintenanceHistoryRequestDTO dto) {
        CourtSlot courtSlot = courtSlotRepository.findById(dto.getCourtSlotId())
                .orElseThrow(() -> new RuntimeException("Court slot not found"));

        LocalDateTime startTime = dto.getStartTime();
        LocalDateTime endTime = dto.getEndTime();

        // Lặp qua từng ngày trong khoảng startTime -> endTime
        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // Xác định khoảng thời gian check trên từng ngày
            LocalDateTime dayStartTime = currentDate.equals(startDate) ? startTime : currentDate.atStartOfDay();
            LocalDateTime dayEndTime = currentDate.equals(endDate) ? endTime : currentDate.atTime(LocalTime.MAX);

            // Gọi API để lấy slot đã booking cho ngày hiện tại
            String url = UriComponentsBuilder.fromHttpUrl("http://203.145.46.242:8080/api/identity/public/booked-slots")
                    .queryParam("courtId", courtSlot.getCourt().getId())
                    .queryParam("bookingDate", currentDate)
                    .toUriString();

            ResponseEntity<UpdateBookingSlot> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            UpdateBookingSlot bookingSlot = response.getBody();

            if (bookingSlot != null && bookingSlot.getCourtSlotBookings() != null) {
                Map<String, List<LocalTime>> courtSlotBookings = bookingSlot.getCourtSlotBookings();
                List<LocalTime> bookedTimes = courtSlotBookings.getOrDefault(courtSlot.getName(), List.of());

                for (LocalTime bookedTime : bookedTimes) {
                    LocalDateTime bookedDateTime = LocalDateTime.of(currentDate, bookedTime);

                    // Nếu thời gian đã đặt nằm trong khoảng bảo trì thì không cho phép tạo lịch bảo trì
                    if (!bookedDateTime.isBefore(dayStartTime) && !bookedDateTime.isAfter(dayEndTime)) {
                        throw new ApiException("CourtSlot '" + courtSlot.getName() +
                                "' đã có booking trong khoảng bảo trì tại thời điểm: " + bookedDateTime, "MAINTENANCE_ERROR");
                    }
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        CourtMaintenanceHistory history = new CourtMaintenanceHistory();
        history.setCourtSlot(courtSlot);
        history.setStartTime(dto.getStartTime());
        history.setEndTime(dto.getEndTime());
        history.setFinishAt(dto.getFinishAt());
        history.setDescription(dto.getDescription());

        maintenanceHistoryRepository.save(history);
        saveMaintenanceToRedis(courtSlot.getCourt().getId(), courtSlot.getId(), dto.getStartTime(), dto.getEndTime());
    }

    public List<CourtMaintenanceHistoryResponseDTO> getHistoriesByCourtSlotId(String courtSlotId) {
        return maintenanceHistoryRepository.findByCourtSlotIdOrderByStartTimeDesc(courtSlotId)
                .stream()
                .map(history -> {
                    CourtMaintenanceHistoryResponseDTO dto = new CourtMaintenanceHistoryResponseDTO();
                    dto.setId(history.getId());
                    dto.setStartTime(history.getStartTime());
                    dto.setEndTime(history.getEndTime());
                    dto.setFinishAt(history.getFinishAt());
                    dto.setDescription(history.getDescription());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public void finishMaintenance(String maintenanceHistoryId) {
        CourtMaintenanceHistory history = maintenanceHistoryRepository.findById(maintenanceHistoryId)
                .orElseThrow(() -> new RuntimeException("Maintenance history not found"));

        history.setFinishAt(LocalDateTime.now());
        maintenanceHistoryRepository.save(history);
    }

    private void saveMaintenanceToRedis(String courtId, String courtSlotId, LocalDateTime startTime, LocalDateTime endTime) {
        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            LocalTime startOfDay = currentDate.equals(startDate) ? startTime.toLocalTime() : LocalTime.MIN;
            LocalTime endOfDay = currentDate.equals(endDate) ? endTime.toLocalTime() : LocalTime.MAX;

            String key = String.format("maintenance:%s:%s:%s", courtId, courtSlotId, currentDate);
            String value = startOfDay + " - " + endOfDay;

            // Tính TTL
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expireAt = LocalDateTime.of(currentDate, endOfDay);
            long ttlInSeconds = Duration.between(now, expireAt).getSeconds();

            if (ttlInSeconds > 0) {
                redisString.opsForValue().set(key, value, ttlInSeconds, TimeUnit.SECONDS);
            }

            currentDate = currentDate.plusDays(1);
        }
    }


}

