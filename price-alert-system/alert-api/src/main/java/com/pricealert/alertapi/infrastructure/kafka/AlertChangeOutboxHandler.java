package com.pricealert.alertapi.infrastructure.kafka;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.kafka.KafkaTopics;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertChangeOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @io.namastack.outbox.annotation.OutboxHandler
    public void handle(AlertChange event, OutboxRecordMetadata metadata) {
        try {
            kafkaTemplate.send(KafkaTopics.ALERT_CHANGES, metadata.getKey(), event)
                    .get(10, TimeUnit.SECONDS);
            log.debug("Published AlertChange {} for alert {} via outbox", event.eventType(), event.alertId());
        } catch (Exception e) {
            log.error("Failed to publish AlertChange for alert {}: {}", event.alertId(), e.getMessage());
            throw new RuntimeException("Kafka send failed for alert " + event.alertId(), e);
        }
    }
}
