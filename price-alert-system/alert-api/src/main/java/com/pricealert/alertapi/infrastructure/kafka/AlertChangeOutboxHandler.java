package com.pricealert.alertapi.infrastructure.kafka;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.kafka.KafkaTopics;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertChangeOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @io.namastack.outbox.annotation.OutboxHandler
    public void handle(AlertChange event, OutboxRecordMetadata metadata) {
        kafkaTemplate.send(KafkaTopics.ALERT_CHANGES, metadata.getKey(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AlertChange for alert {}: {}", event.alertId(), ex.getMessage());
                        throw new RuntimeException("Kafka send failed for alert " + event.alertId(), ex);
                    }
                    log.debug("Published AlertChange {} for alert {} via outbox", event.eventType(), event.alertId());
                });
    }
}
