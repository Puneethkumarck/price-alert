package com.pricealert.alertapi.application.controller.notification.mapper;

import com.pricealert.alertapi.application.controller.notification.NotificationResponse;
import com.pricealert.alertapi.domain.notification.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationResponseMapper {

    NotificationResponse toResponse(Notification notification);
}
