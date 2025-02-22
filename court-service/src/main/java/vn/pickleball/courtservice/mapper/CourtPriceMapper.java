package vn.pickleball.courtservice.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtPrice;
import vn.pickleball.courtservice.model.request.CourtRequest;
import vn.pickleball.courtservice.model.response.CourtPriceResponse;
import vn.pickleball.courtservice.model.response.CourtResponse;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true), imports = {String.class})
public abstract class CourtPriceMapper {
    public abstract CourtPriceResponse toCourtPriceResponse(CourtPrice courtPrice);
}
