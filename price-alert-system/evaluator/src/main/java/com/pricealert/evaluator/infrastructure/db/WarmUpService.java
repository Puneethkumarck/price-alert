package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import com.pricealert.evaluator.domain.evaluation.AlertEntry;
import com.pricealert.evaluator.domain.evaluation.AlertIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, loads all ACTIVE alerts from PostgreSQL and builds in-memory indices.
 * TRIGGERED_TODAY alerts are excluded — they've already fired for the current trading day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarmUpService {

    private final AlertWarmUpRepository repository;
    private final AlertIndexManager indexManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUp() {
        log.info("Starting evaluator warm-up: loading ACTIVE alerts from DB");
        var activeAlerts = repository.findByStatus(AlertStatus.ACTIVE);

        for (var row : activeAlerts) {
            var entry = AlertEntry.builder()
                    .alertId(row.getId())
                    .userId(row.getUserId())
                    .symbol(row.getSymbol())
                    .thresholdPrice(row.getThresholdPrice())
                    .direction(row.getDirection())
                    .note(row.getNote())
                    .build();
            indexManager.addAlert(entry);
        }

        log.info("Evaluator warm-up complete: loaded {} alerts across {} symbols",
                indexManager.totalAlerts(), indexManager.symbolCount());
    }
}
