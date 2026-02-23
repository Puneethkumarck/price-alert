package com.pricealert.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder(toBuilder = true)
public record MarketTick(
        String symbol,
        BigDecimal price,
        BigDecimal bid,
        BigDecimal ask,
        long volume,
        Instant timestamp,
        long sequence
) {

    @JsonProperty("type")
    public String type() {
        return "TICK";
    }
}
