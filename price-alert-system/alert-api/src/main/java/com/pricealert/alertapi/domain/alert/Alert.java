package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder(toBuilder = true)
public record Alert(
        String id,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        Direction direction,
        AlertStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Instant lastTriggeredAt,
        BigDecimal lastTriggerPrice
) {
}
