package vn.pickleball.courtservice.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtResponse;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class CourtMapper {
    public abstract Court courtRequestToCourt(CourtRequest courtRequest);

    public abstract void updateCourt(@MappingTarget Court court , CourtRequest request);

    public abstract CourtResponse courtToCourtResponse(Court court);

    public abstract List<CourtResponse> courtsToCourtResponses(List<Court> courts);
}
