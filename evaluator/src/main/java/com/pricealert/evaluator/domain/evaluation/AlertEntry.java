package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import lombok.Builder;

@Builder(toBuilder = true)
public record AlertEntry(
        String alertId,
        String userId,
        String symbol,
        BigDecimal thresholdPrice,
        Direction direction,
        String note) {}
