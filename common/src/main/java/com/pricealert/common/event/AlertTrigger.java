package com.pricealert.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

@Builder(toBuilder = true)
public record AlertTrigger(
        @JsonProperty("trigger_id") String triggerId,
        @JsonProperty("alert_id") String alertId,
        @JsonProperty("user_id") String userId,
        String symbol,
        @JsonProperty("threshold_price") BigDecimal thresholdPrice,
        @JsonProperty("trigger_price") BigDecimal triggerPrice,
        Direction direction,
        String note,
        @JsonProperty("tick_timestamp") Instant tickTimestamp,
        @JsonProperty("triggered_at") Instant triggeredAt,
        @JsonProperty("trading_date") LocalDate tradingDate) {}
