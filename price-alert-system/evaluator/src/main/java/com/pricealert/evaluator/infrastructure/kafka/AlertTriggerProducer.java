package com.pricealert.evaluator.infrastructure.kafka;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces AlertTrigger events to the alert-triggers topic, keyed by user_id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggerProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(AlertTrigger trigger) {
        kafkaTemplate.send(KafkaTopics.ALERT_TRIGGERS, trigger.userId(), trigger)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to produce AlertTrigger for alert {}: {}",
                                trigger.alertId(), ex.getMessage());
                    } else {
                        log.debug("Produced AlertTrigger for alert {} to partition {}",
                                trigger.alertId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
