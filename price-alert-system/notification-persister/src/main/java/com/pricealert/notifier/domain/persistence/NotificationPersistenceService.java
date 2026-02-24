package com.pricealert.notifier.domain.persistence;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.id.UlidGenerator;
import com.pricealert.notifier.infrastructure.db.AlertTriggerLogJpaRepository;
import com.pricealert.notifier.infrastructure.db.AlertTriggerLogRow;
import com.pricealert.notifier.infrastructure.db.NotificationJpaRepository;
import com.pricealert.notifier.infrastructure.db.NotificationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Idempotent persistence of alert trigger events.
 * Layer 3: notifications.idempotency_key UNIQUE constraint (key = {alert_id}:{trading_date}).
 * Layer 4: alert_trigger_log unique index on (alert_id, trading_date).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationJpaRepository notificationRepository;
    private final AlertTriggerLogJpaRepository triggerLogRepository;

    @Transactional
    public boolean persist(AlertTrigger trigger) {
        var idempotencyKey = trigger.alertId() + ":" + trigger.tradingDate();

        // Layer 3+4: INSERT ... ON CONFLICT DO NOTHING
        var notification = NotificationRow.builder()
                .id(UlidGenerator.generate())
                .alertTriggerId(trigger.triggerId())
                .alertId(trigger.alertId())
                .userId(trigger.userId())
                .symbol(trigger.symbol())
                .thresholdPrice(trigger.thresholdPrice())
                .triggerPrice(trigger.triggerPrice())
                .direction(trigger.direction().name())
                .note(trigger.note())
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .read(false)
                .build();

        var triggerLog = AlertTriggerLogRow.builder()
                .id(UlidGenerator.generate())
                .alertId(trigger.alertId())
                .userId(trigger.userId())
                .symbol(trigger.symbol())
                .thresholdPrice(trigger.thresholdPrice())
                .triggerPrice(trigger.triggerPrice())
                .tickTimestamp(trigger.tickTimestamp())
                .triggeredAt(trigger.triggeredAt())
                .tradingDate(trigger.tradingDate())
                .build();

        notificationRepository.insertIdempotent(notification);
        triggerLogRepository.insertIdempotent(triggerLog);

        // Check if notification was actually inserted (not a duplicate)
        var inserted = notificationRepository.existsByIdempotencyKey(idempotencyKey);
        if (inserted) {
            log.info("notification.persisted: alert_id={}, user_id={}, symbol={}, trigger_price={}",
                    trigger.alertId(), trigger.userId(), trigger.symbol(), trigger.triggerPrice());
        } else {
            log.debug("Duplicate notification skipped for alert {} on {}", trigger.alertId(), trigger.tradingDate());
        }
        return inserted;
    }
}
