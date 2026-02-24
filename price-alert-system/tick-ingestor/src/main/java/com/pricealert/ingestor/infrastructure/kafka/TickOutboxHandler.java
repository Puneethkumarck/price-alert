package com.pricealert.ingestor.infrastructure.kafka;

import com.pricealert.common.event.MarketTick;
import com.pricealert.common.kafka.KafkaTopics;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @io.namastack.outbox.annotation.OutboxHandler
    public void handle(MarketTick tick, OutboxRecordMetadata metadata) {
        kafkaTemplate.send(KafkaTopics.MARKET_TICKS, metadata.getKey(), tick)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish MarketTick for {}: {}", tick.symbol(), ex.getMessage());
                        throw new RuntimeException("Kafka send failed for tick " + tick.symbol(), ex);
                    }
                });
    }
}
