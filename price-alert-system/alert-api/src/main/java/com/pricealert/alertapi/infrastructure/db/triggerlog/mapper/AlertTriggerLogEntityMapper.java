package com.pricealert.alertapi.infrastructure.db.triggerlog.mapper;

import com.pricealert.alertapi.domain.triggerlog.AlertTriggerLog;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertTriggerLogEntityMapper {

    AlertTriggerLogEntity toEntity(AlertTriggerLog triggerLog);

    AlertTriggerLog toDomain(AlertTriggerLogEntity entity);
}
