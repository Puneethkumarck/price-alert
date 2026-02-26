package com.pricealert.alertapi.application.job;

import com.pricealert.alertapi.domain.alert.AlertEventPublisher;
import com.pricealert.alertapi.domain.alert.AlertRepository;
import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily reset scheduler: TRIGGERED_TODAY → ACTIVE at market open.
 * Uses pg_try_advisory_lock to prevent concurrent execution across instances.
 * Publishes RESET events to alert-changes topic so evaluator re-indexes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyResetScheduler {

    private static final long ADVISORY_LOCK_ID = 1001L;

    private final AlertRepository alertRepository;
    private final AlertEventPublisher eventPublisher;
    private final EntityManager entityManager;

    @Scheduled(cron = "${alert.daily-reset.cron}", zone = "${alert.daily-reset.timezone}")
    @Transactional
    public void resetTriggeredAlerts() {
        if (!acquireAdvisoryLock()) {
            log.info("Daily reset: another instance holds the lock, skipping");
            return;
        }

        log.info("Daily reset: starting TRIGGERED_TODAY → ACTIVE reset");

        // Find all TRIGGERED_TODAY alerts before updating so we can publish RESET events
        var triggeredAlerts = alertRepository.findByStatus(AlertStatus.TRIGGERED_TODAY);

        if (triggeredAlerts.isEmpty()) {
            log.info("Daily reset: no TRIGGERED_TODAY alerts to reset");
            return;
        }

        // Batch update: TRIGGERED_TODAY → ACTIVE
        var updatedCount =
                alertRepository.updateStatusByCurrentStatus(
                        AlertStatus.ACTIVE, AlertStatus.TRIGGERED_TODAY);

        // Publish RESET events for each alert so evaluator re-indexes them
        for (var alert : triggeredAlerts) {
            var event =
                    AlertChange.builder()
                            .eventType(AlertChangeType.RESET)
                            .alertId(alert.id())
                            .userId(alert.userId())
                            .symbol(alert.symbol())
                            .thresholdPrice(alert.thresholdPrice())
                            .direction(alert.direction())
                            .timestamp(Instant.now())
                            .build();
            eventPublisher.publish(event);
        }

        log.info(
                "Daily reset complete: {} alerts reset to ACTIVE, {} RESET events published",
                updatedCount,
                triggeredAlerts.size());
    }

    private boolean acquireAdvisoryLock() {
        var result =
                entityManager
                        .createNativeQuery("SELECT pg_try_advisory_lock(:lockId)")
                        .setParameter("lockId", ADVISORY_LOCK_ID)
                        .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
