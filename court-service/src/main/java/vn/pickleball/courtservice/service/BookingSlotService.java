package vn.pickleball.courtservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import vn.pickleball.courtservice.entity.CourtPrice;
import vn.pickleball.courtservice.entity.CourtSlot;
import vn.pickleball.courtservice.entity.TimeSlot;
import vn.pickleball.courtservice.mapper.TimeSlotMapper;
import vn.pickleball.courtservice.model.BookingStatus;
import vn.pickleball.courtservice.model.WeekType;
import vn.pickleball.courtservice.model.response.BookingSlotResponse;
import vn.pickleball.courtservice.model.response.CourtSlotBookingResponse;
import vn.pickleball.courtservice.repository.CourtPriceRepository;
import vn.pickleball.courtservice.repository.CourtSlotRepository;
import vn.pickleball.courtservice.repository.TimeSlotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BookingSlotService {

    private final CourtSlotRepository courtSlotRepository;

    private final CourtPriceRepository courtPriceRepository;

    private final TimeSlotRepository timeSlotRepository;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    private final TimeSlotMapper timeSlotMapper;

    private static final String REDIS_KEY_PREFIX = "booking_slots:";

    public List<CourtSlotBookingResponse> getBookingSlots(String courtId, LocalDate dateBooking) {
        String redisKey = REDIS_KEY_PREFIX + courtId + ":" + dateBooking.toString();

        // Kiểm tra trong Redis
        List<CourtSlotBookingResponse> cachedSlots = (List<CourtSlotBookingResponse>) redisTemplate.opsForValue().get(redisKey);
        if (cachedSlots != null) {
            return cachedSlots;
        }

        // Lấy tất cả CourtSlot theo courtId
        List<CourtSlot> courtSlots = courtSlotRepository.findByCourtId(courtId);

        // Lấy tất cả CourtPrice của courtId
        List<CourtPrice> courtPrices = courtPriceRepository.findByCourtId(courtId);

        // Kiểm tra xem có bao nhiêu loại weekType
        boolean hasMultipleWeekTypes = courtPrices.stream()
                .map(CourtPrice::getWeekType)
                .distinct()
                .count() > 1;

        // Xác định weekType dựa trên ngày được chọn (nếu có nhiều loại weekType)
        WeekType selectedWeekType = WeekType.NONE;
        if (hasMultipleWeekTypes) {
            selectedWeekType = determineWeekType(dateBooking);
        }

        // Tạo danh sách CourtSlotBookingResponse
        List<CourtSlotBookingResponse> bookingSlotsByCourtSlot = new ArrayList<>();

        for (CourtSlot courtSlot : courtSlots) {
            // Lấy tất cả TimeSlot của CourtSlot
            List<TimeSlot> timeSlots = timeSlotRepository.findByCourtId(courtSlot.getCourt().getId());

            // Tạo danh sách BookingSlotResponse cho từng CourtSlot
            List<BookingSlotResponse> bookingSlots = new ArrayList<>();

            for (TimeSlot timeSlot : timeSlots) {
                // Lấy CourtPrice tương ứng với TimeSlot
                CourtPrice courtPrice = courtPrices.stream()
                        .filter(cp -> cp.getTimeSlot().getId().equals(timeSlot.getId()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("CourtPrice not found for TimeSlot: " + timeSlot.getId()));

                // Kiểm tra weekType (nếu có nhiều loại)
                if (hasMultipleWeekTypes && courtPrice.getWeekType() != selectedWeekType) {
                    continue; // Bỏ qua nếu weekType không khớp
                }

                // Chia nhỏ TimeSlot thành các khoảng thời gian nhỏ hơn
                List<BookingSlotResponse> splitSlots = splitTimeSlot(timeSlot, dateBooking, courtPrice);

                // Thêm vào danh sách
                bookingSlots.addAll(splitSlots);
            }

            // Tạo CourtSlotBookingResponse
            CourtSlotBookingResponse courtSlotBookingResponse = new CourtSlotBookingResponse();
            courtSlotBookingResponse.setCourtSlotId(courtSlot.getId());
            courtSlotBookingResponse.setBookingSlots(bookingSlots);

            // Thêm vào kết quả
            bookingSlotsByCourtSlot.add(courtSlotBookingResponse);
        }

        // Tính thời gian hết hạn cho Redis (24h của ngày được chọn trừ cho ngày hiện tại)
        long expireTime = calculateExpireTime(dateBooking);

        // Lưu vào Redis với thời gian hết hạn
        redisTemplate.opsForValue().set(redisKey, bookingSlotsByCourtSlot, expireTime, TimeUnit.SECONDS);

        return bookingSlotsByCourtSlot;
    }

    private WeekType determineWeekType(LocalDate dateBooking) {
        DayOfWeek dayOfWeek = dateBooking.getDayOfWeek();
        return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) ? WeekType.WEEKEND : WeekType.WEEKDAY;
    }

    private List<BookingSlotResponse> splitTimeSlot(TimeSlot timeSlot, LocalDate dateBooking, CourtPrice courtPrice) {
        List<BookingSlotResponse> splitSlots = new ArrayList<>();

        LocalTime startTime = timeSlot.getStartTime();
        LocalTime endTime = timeSlot.getEndTime();

        // Chia thành các khoảng thời gian 30 phút
        while (startTime.isBefore(endTime)) {
            LocalTime slotEndTime = startTime.plusMinutes(30);
            if (slotEndTime.isAfter(endTime)) {
                slotEndTime = endTime;
            }

            // Tạo BookingSlotResponse
            BookingSlotResponse slotResponse = new BookingSlotResponse();
            slotResponse.setStartTime(startTime);
            slotResponse.setEndTime(slotEndTime);
            slotResponse.setRegularPrice(courtPrice.getRegularPrice()); // Giá từ CourtPrice
            slotResponse.setDailyPrice(courtPrice.getDailyPrice());
            slotResponse.setStudentPrice(courtPrice.getStudentPrice());
            slotResponse.setStatus(BookingStatus.AVAILABLE); // Trạng thái mặc định

            splitSlots.add(slotResponse);

            // Cập nhật startTime cho vòng lặp tiếp theo
            startTime = slotEndTime;
        }

        return splitSlots;
    }

    private long calculateExpireTime(LocalDate dateBooking) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = dateBooking.atTime(23, 59, 59); // Cuối ngày được chọn
        return Duration.between(now, endOfDay).getSeconds(); // Thời gian còn lại đến cuối ngày
    }

    public List<CourtSlotBookingResponse> getBookingSlotsRedis(String courtId, LocalDate dateBooking) {
        String redisKey = REDIS_KEY_PREFIX + courtId + ":" + dateBooking.toString();

        // Lấy dữ liệu từ Redis
        Object cachedData = redisTemplate.opsForValue().get(redisKey);
        if (cachedData == null) {
            throw new RuntimeException("Không tìm thấy thông tin booking slot trong Redis");
        }

        // Chuyển đổi dữ liệu từ Redis thành List<CourtSlotBookingResponse>
        List<CourtSlotBookingResponse> cachedSlots = objectMapper.convertValue(cachedData, new TypeReference<List<CourtSlotBookingResponse>>() {});

        return cachedSlots;
    }

    public void updateBookingSlotsInRedis(String courtId, LocalDate dateBooking, Map<String, List<LocalTime>> courtSlotBookings) {
        String redisKey = REDIS_KEY_PREFIX + courtId + ":" + dateBooking.toString();

        // Lấy dữ liệu từ Redis
        Object cachedData = redisTemplate.opsForValue().get(redisKey);
        if (cachedData == null) {
            throw new RuntimeException("Không tìm thấy thông tin booking slot trong Redis");
        }

        // Chuyển đổi dữ liệu từ Redis thành List<CourtSlotBookingResponse>
        List<CourtSlotBookingResponse> cachedSlots = objectMapper.convertValue(cachedData, new TypeReference<List<CourtSlotBookingResponse>>() {});

        // Duyệt qua từng courtSlotId và cập nhật trạng thái
        for (Map.Entry<String, List<LocalTime>> entry : courtSlotBookings.entrySet()) {
            String courtSlotId = entry.getKey();
            List<LocalTime> startTimes = entry.getValue();

            // Tìm CourtSlotBookingResponse tương ứng với courtSlotId
            CourtSlotBookingResponse courtSlotBookingResponse = cachedSlots.stream()
                    .filter(response -> response.getCourtSlotId().equals(courtSlotId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy courtSlotId trong Redis: " + courtSlotId));

            // Cập nhật trạng thái của các khung giờ được đặt
            List<BookingSlotResponse> bookingSlots = courtSlotBookingResponse.getBookingSlots();
            for (BookingSlotResponse slot : bookingSlots) {
                if (startTimes.contains(slot.getStartTime())) {
                    slot.setStatus(BookingStatus.BOOKED); // Cập nhật trạng thái thành BOOKED
                }
            }
        }

        // Lưu lại vào Redis
        redisTemplate.opsForValue().set(redisKey, cachedSlots);
    }
}