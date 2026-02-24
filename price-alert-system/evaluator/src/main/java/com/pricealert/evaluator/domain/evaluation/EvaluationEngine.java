package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.id.UlidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Core evaluation logic: given a symbol + new price, determines which alerts fire
 * and produces AlertTrigger events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationEngine {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private final AlertIndexManager indexManager;

    /**
     * Evaluate a price tick against all alerts for the given symbol.
     *
     * @return list of AlertTrigger events for fired alerts (empty if none fire)
     */
    public List<AlertTrigger> evaluate(String symbol, BigDecimal newPrice, Instant tickTimestamp) {
        var index = indexManager.get(symbol);
        if (index == null) {
            return Collections.emptyList();
        }

        var firedAlerts = index.evaluate(newPrice);
        if (firedAlerts.isEmpty()) {
            return Collections.emptyList();
        }

        var now = Instant.now();
        var tradingDate = LocalDate.ofInstant(tickTimestamp, NY_ZONE);

        return firedAlerts.stream()
                .map(alert -> AlertTrigger.builder()
                        .triggerId(UlidGenerator.generate())
                        .alertId(alert.alertId())
                        .userId(alert.userId())
                        .symbol(symbol)
                        .thresholdPrice(alert.thresholdPrice())
                        .triggerPrice(newPrice)
                        .direction(alert.direction())
                        .note(alert.note())
                        .tickTimestamp(tickTimestamp)
                        .triggeredAt(now)
                        .tradingDate(tradingDate)
                        .build())
                .toList();
    }
}
