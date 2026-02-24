package com.pricealert.notifier.infrastructure.kafka;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.kafka.KafkaTopics;
import com.pricealert.notifier.domain.persistence.NotificationPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for alert-triggers topic.
 * Persists notifications and trigger logs idempotently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggerConsumer {

    private final NotificationPersistenceService persistenceService;

    @KafkaListener(
            topics = KafkaTopics.ALERT_TRIGGERS,
            groupId = "notification-persister-group",
            containerFactory = "alertTriggerListenerContainerFactory"
    )
    public void onAlertTrigger(AlertTrigger trigger) {
        log.debug("Received AlertTrigger: alert_id={}, symbol={}, trigger_price={}",
                trigger.alertId(), trigger.symbol(), trigger.triggerPrice());
        persistenceService.persist(trigger);
    }
}
