package com.pricealert.alertapi.infrastructure.kafka;

import com.pricealert.alertapi.domain.alert.AlertEventPublisher;
import com.pricealert.common.event.AlertChange;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertChangePublisher implements AlertEventPublisher {

    private final Outbox outbox;

    @Override
    public void publish(AlertChange event) {
        outbox.schedule(event, event.symbol());
        log.debug("Scheduled AlertChange {} for alert {} to outbox", event.eventType(), event.alertId());
    }
}
