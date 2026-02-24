package com.pricealert.alertapi.application.controller.alert.mapper;

import com.pricealert.alertapi.application.controller.alert.AlertResponse;
import com.pricealert.alertapi.domain.alert.Alert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertRequestResponseMapper {

    AlertResponse toResponse(Alert alert);
}
