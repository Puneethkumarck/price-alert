package com.pricealert.notifier.infrastructure.kafka;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.kafka.KafkaTopics;
import com.pricealert.notifier.domain.persistence.NotificationPersistenceService;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggerConsumer {

    private final NotificationPersistenceService persistenceService;
    private final Counter notificationsPersistedCounter;
    private final Counter notificationsDeduplicatedCounter;

    @KafkaListener(
            topics = KafkaTopics.ALERT_TRIGGERS,
            groupId = "notification-persister-group",
            containerFactory = "alertTriggerListenerContainerFactory"
    )
    public void onAlertTrigger(AlertTrigger trigger) {
        log.debug("Received AlertTrigger: alert_id={}, symbol={}, trigger_price={}",
                trigger.alertId(), trigger.symbol(), trigger.triggerPrice());
        var inserted = persistenceService.persist(trigger);
        if (inserted) {
            notificationsPersistedCounter.increment();
        } else {
            notificationsDeduplicatedCounter.increment();
        }
    }
}
