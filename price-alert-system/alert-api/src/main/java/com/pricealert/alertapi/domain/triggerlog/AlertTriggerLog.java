package com.pricealert.alertapi.domain.triggerlog;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Builder(toBuilder = true)
public record AlertTriggerLog(
        String id,
        String alertId,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        BigDecimal triggerPrice,
        Instant tickTimestamp,
        Instant triggeredAt,
        LocalDate tradingDate
) {
}
