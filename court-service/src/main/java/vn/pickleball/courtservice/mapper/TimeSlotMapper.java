package vn.pickleball.courtservice.mapper;

import org.mapstruct.*;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.TimeSlot;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.request.TimeSlotRequest;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.TimeSlotResponse;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class TimeSlotMapper {

    public abstract TimeSlot timeSlotRequestToTimeSlot(TimeSlotRequest timeSlotRequest);

    @Mapping(source = "courtPrice.regularPrice", target = "regularPrice")
    @Mapping(source = "courtPrice.dailyPrice", target = "dailyPrice")
    @Mapping(source = "courtPrice.studentPrice", target = "studentPrice")
    public  abstract TimeSlotResponse timeSlotToTimeSlotResponse(TimeSlot timeSlot);

    public abstract List<TimeSlotResponse> toTimeSlotResponseList(List<TimeSlot> timeSlots);
}
