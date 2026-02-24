package com.pricealert.alertapi.infrastructure.kafka;

import com.pricealert.alertapi.domain.alert.AlertEventPublisher;
import com.pricealert.common.event.AlertChange;
import com.pricealert.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertChangePublisher implements AlertEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AlertChange event) {
        kafkaTemplate.send(KafkaTopics.ALERT_CHANGES, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AlertChange for alert {}: {}", event.alertId(), ex.getMessage());
                    } else {
                        log.debug("Published AlertChange {} for alert {}", event.eventType(), event.alertId());
                    }
                });
    }
}
