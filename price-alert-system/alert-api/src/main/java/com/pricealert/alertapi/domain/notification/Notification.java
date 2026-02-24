package com.pricealert.alertapi.domain.notification;

import com.pricealert.common.event.Direction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder(toBuilder = true)
public record Notification(
        String id,
        String alertTriggerId,
        String alertId,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        BigDecimal triggerPrice,
        Direction direction,
        String note,
        String idempotencyKey,
        Instant createdAt,
        boolean read
) {
}
