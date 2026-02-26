package com.pricealert.alertapi.application.controller.notification;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import java.time.Instant;

public record NotificationResponse(
        String id,
        String alertTriggerId,
        String alertId,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        BigDecimal triggerPrice,
        Direction direction,
        String note,
        Instant createdAt,
        boolean read) {}
