package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import com.pricealert.evaluator.application.config.EvaluatorProperties;
import com.pricealert.evaluator.domain.evaluation.AlertEntry;
import com.pricealert.evaluator.domain.evaluation.AlertIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmUpService {

    private final AlertWarmUpRepository repository;
    private final AlertIndexManager indexManager;
    private final EvaluatorProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUp() {
        log.info("Starting evaluator warm-up: loading ACTIVE alerts from DB (batch-size={})",
                properties.warmup().batchSize());

        int page = 0;
        int batchSize = properties.warmup().batchSize();
        Page<AlertRow> batch;

        do {
            batch = repository.findByStatus(AlertStatus.ACTIVE, PageRequest.of(page++, batchSize));
            batch.forEach(row -> indexManager.addAlert(AlertEntry.builder()
                    .alertId(row.getId())
                    .userId(row.getUserId())
                    .symbol(row.getSymbol())
                    .thresholdPrice(row.getThresholdPrice())
                    .direction(row.getDirection())
                    .note(row.getNote())
                    .build()));
            log.debug("Warm-up page {}: loaded {} alerts", page, batch.getNumberOfElements());
        } while (batch.hasNext());

        log.info("Evaluator warm-up complete: loaded {} alerts across {} symbols",
                indexManager.totalAlerts(), indexManager.symbolCount());
    }
}
