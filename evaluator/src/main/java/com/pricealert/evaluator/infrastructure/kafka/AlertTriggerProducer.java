package com.pricealert.evaluator.infrastructure.kafka;

import com.pricealert.common.event.AlertTrigger;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Schedules AlertTrigger events to the transactional outbox, keyed by user_id.
 * The outbox handler ({@link AlertTriggerOutboxHandler}) publishes to Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggerProducer {

    private final Outbox outbox;

    public void send(AlertTrigger trigger) {
        outbox.schedule(trigger, trigger.userId());
        log.debug("Scheduled AlertTrigger for alert {} to outbox", trigger.alertId());
    }
}
