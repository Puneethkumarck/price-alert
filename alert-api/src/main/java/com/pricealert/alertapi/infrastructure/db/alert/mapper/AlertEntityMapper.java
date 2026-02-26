package com.pricealert.alertapi.infrastructure.db.alert.mapper;

import com.pricealert.alertapi.domain.alert.Alert;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertEntityMapper {

    AlertEntity toEntity(Alert alert);

    Alert toDomain(AlertEntity entity);
}
