package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.id.UlidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationEngine {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private final AlertIndexManager indexManager;

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
                .map(
                        alert ->
                                AlertTrigger.builder()
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
