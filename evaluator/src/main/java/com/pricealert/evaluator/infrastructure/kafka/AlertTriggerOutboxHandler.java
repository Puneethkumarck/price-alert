package com.pricealert.evaluator.infrastructure.kafka;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.kafka.KafkaTopics;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggerOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @io.namastack.outbox.annotation.OutboxHandler
    public void handle(AlertTrigger trigger, OutboxRecordMetadata metadata) {
        try {
            kafkaTemplate
                    .send(KafkaTopics.ALERT_TRIGGERS, metadata.getKey(), trigger)
                    .get(10, TimeUnit.SECONDS);
            log.debug("Published AlertTrigger for alert {} via outbox", trigger.alertId());
        } catch (Exception e) {
            log.error(
                    "Failed to publish AlertTrigger for alert {}: {}",
                    trigger.alertId(),
                    e.getMessage());
            throw new RuntimeException("Kafka send failed for alert " + trigger.alertId(), e);
        }
    }
}
