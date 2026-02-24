package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record AlertEntry(
        String alertId,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        Direction direction,
        String note
) {
}
