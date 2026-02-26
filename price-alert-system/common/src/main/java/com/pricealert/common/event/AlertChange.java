package com.pricealert.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record AlertChange(
        @JsonProperty("event_type") AlertChangeType eventType,
        @JsonProperty("alert_id") String alertId,
        @JsonProperty("user_id") String userId,
        String symbol,
        @JsonProperty("threshold_price") BigDecimal thresholdPrice,
        Direction direction,
        Instant timestamp) {}
