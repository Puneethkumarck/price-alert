package com.pricealert.alertapi.domain.triggerlog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

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
        LocalDate tradingDate) {}
