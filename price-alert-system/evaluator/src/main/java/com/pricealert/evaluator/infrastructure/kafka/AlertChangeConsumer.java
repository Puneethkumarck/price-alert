package com.pricealert.evaluator.infrastructure.kafka;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.kafka.KafkaTopics;
import com.pricealert.evaluator.domain.evaluation.AlertEntry;
import com.pricealert.evaluator.domain.evaluation.AlertIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for alert-changes topic.
 * Handles CREATED, UPDATED, DELETED, RESET events to maintain in-memory index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertChangeConsumer {

    private final AlertIndexManager indexManager;

    @KafkaListener(
            topics = KafkaTopics.ALERT_CHANGES,
            groupId = "evaluator-changes",
            containerFactory = "alertChangeListenerContainerFactory"
    )
    public void onAlertChange(AlertChange change) {
        switch (change.eventType()) {
            case CREATED -> handleCreated(change);
            case UPDATED -> handleUpdated(change);
            case DELETED -> handleDeleted(change);
            case RESET -> handleReset(change);
        }
    }

    private void handleCreated(AlertChange change) {
        log.debug("Adding alert {} for {} to index", change.alertId(), change.symbol());
        // Remove first to be idempotent (warm-up may have already loaded this alert)
        indexManager.removeAlert(change.alertId(), change.symbol());
        indexManager.addAlert(toEntry(change));
    }

    private void handleUpdated(AlertChange change) {
        log.debug("Updating alert {} for {} in index", change.alertId(), change.symbol());
        indexManager.removeAlert(change.alertId(), change.symbol());
        indexManager.addAlert(toEntry(change));
    }

    private void handleDeleted(AlertChange change) {
        log.debug("Removing alert {} for {} from index", change.alertId(), change.symbol());
        indexManager.removeAlert(change.alertId(), change.symbol());
    }

    private void handleReset(AlertChange change) {
        log.debug("Re-adding alert {} for {} to index (daily reset)", change.alertId(), change.symbol());
        // Remove first to be idempotent
        indexManager.removeAlert(change.alertId(), change.symbol());
        indexManager.addAlert(toEntry(change));
    }

    private AlertEntry toEntry(AlertChange change) {
        return AlertEntry.builder()
                .alertId(change.alertId())
                .userId(change.userId())
                .symbol(change.symbol())
                .thresholdPrice(change.thresholdPrice())
                .direction(change.direction())
                .build();
    }
}
