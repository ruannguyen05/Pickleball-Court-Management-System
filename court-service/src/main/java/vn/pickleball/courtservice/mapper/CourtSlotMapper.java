package vn.pickleball.courtservice.mapper;

import org.mapstruct.*;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtSlot;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.request.CourtSlotRequest;
import vn.pickleball.courtservice.model.response.CourtResponse;
import vn.pickleball.courtservice.model.response.CourtSlotResponse;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class CourtSlotMapper {
    @Mapping(target = "court", ignore = true)
    public abstract CourtSlot courtSlotRequestToCourtSlot(CourtSlotRequest courtSlotRequest);

    @Mapping(source = "court.id", target = "courtId")
    public abstract CourtSlotResponse courtSlotToCourtSlotResponse(CourtSlot courtSlot);
}
