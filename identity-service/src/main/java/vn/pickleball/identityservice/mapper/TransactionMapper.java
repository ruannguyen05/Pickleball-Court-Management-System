package vn.pickleball.identityservice.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import vn.pickleball.identityservice.dto.request.TransactionRequest;
import vn.pickleball.identityservice.entity.Transaction;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(target = "order", ignore = true)
    Transaction toEntity(TransactionRequest request);

    @Mapping(source = "order.id", target = "orderId")
    TransactionRequest toDto(Transaction transaction);
}

