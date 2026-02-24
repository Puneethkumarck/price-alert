package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async DB status update: ACTIVE → TRIGGERED_TODAY.
 * Layer 2 dedup: conditional UPDATE skips if affected_rows=0.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertStatusUpdater {

    private final AlertWarmUpRepository repository;

    @Async
    @Transactional
    public void markTriggeredToday(String alertId) {
        try {
            var updated = repository.updateStatusByIdAndCurrentStatus(
                    alertId, AlertStatus.TRIGGERED_TODAY, AlertStatus.ACTIVE);
            if (updated == 0) {
                log.debug("Skipped status update for alert {} (not ACTIVE — Layer 2 dedup)", alertId);
            } else {
                log.debug("Marked alert {} as TRIGGERED_TODAY", alertId);
            }
        } catch (Exception e) {
            log.warn("Failed to update alert {} status to TRIGGERED_TODAY: {}", alertId, e.getMessage());
        }
    }
}
