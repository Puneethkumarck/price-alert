package com.pricealert.alertapi.infrastructure.db.notification.mapper;

import com.pricealert.alertapi.domain.notification.Notification;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationEntityMapper {

    NotificationEntity toEntity(Notification notification);

    Notification toDomain(NotificationEntity entity);
}
