package com.pricealert.notifier.domain.persistence;

import com.pricealert.common.event.AlertTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationPort notificationPort;
    private final AlertTriggerLogPort triggerLogPort;

    public boolean persist(AlertTrigger trigger) {
        var idempotencyKey = trigger.alertId() + ":" + trigger.tradingDate();

        // Layer 3+4: INSERT ... ON CONFLICT DO NOTHING (delegated to infrastructure adapters)
        notificationPort.insertIdempotent(trigger, idempotencyKey);
        triggerLogPort.insertIdempotent(trigger);

        // Check if notification was actually inserted (not a duplicate)
        var inserted = notificationPort.existsByIdempotencyKey(idempotencyKey);
        if (inserted) {
            log.info("notification.persisted: alert_id={}, user_id={}, symbol={}, trigger_price={}",
                    trigger.alertId(), trigger.userId(), trigger.symbol(), trigger.triggerPrice());
        } else {
            log.debug("Duplicate notification skipped for alert {} on {}", trigger.alertId(), trigger.tradingDate());
        }
        return inserted;
    }
}
