package vn.pickleball.courtservice.mapper;

import org.mapstruct.*;
import vn.pickleball.courtservice.entity.CourtServiceEntity;
import vn.pickleball.courtservice.model.request.CourtServiceRequest;
import vn.pickleball.courtservice.model.response.CourtServiceResponse;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class CourtServiceMapper {
    public abstract CourtServiceEntity toEntity(CourtServiceRequest request);

    @Mapping(target = "soldCount", ignore = true)
    public abstract void updateEntityFromRequest(CourtServiceRequest request, @MappingTarget CourtServiceEntity courtService);

    @Mapping(target = "courtId" , source = "courtService.court.id")
    public abstract CourtServiceResponse toResponse(CourtServiceEntity courtService);
}