package com.group1.car_rental.mapper;

import com.group1.car_rental.dto.CarsDto;
import com.group1.car_rental.entity.Cars;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CarsMapper {

    @Mapping(target = "vinEncrypted", source = "vinEncrypted")
    @Mapping(target = "plateMasked", source = "plateMasked")
    @Mapping(target = "imageUrls", source = "imageUrls")
    // XÓA TẤT CẢ @Mapping(target = "...", ignore = true)
    // → MapStruct tự động bỏ qua field không có trong DTO
    CarsDto toDto(Cars cars);

    List<CarsDto> toDtoList(List<Cars> cars);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "vinEncrypted", source = "vinEncrypted")
    @Mapping(target = "plateMasked", source = "plateMasked")
    @Mapping(target = "imageUrls", source = "imageUrls")
    // XÓA TẤT CẢ @Mapping(target = "...", ignore = true)
    void updateVehicleFromDto(CarsDto dto, @MappingTarget Cars cars);
}