package com.pricealert.ingestor.infrastructure.kafka;

import com.pricealert.common.event.MarketTick;
import com.pricealert.common.kafka.KafkaTopics;
import io.namastack.outbox.annotation.OutboxHandler;
import io.namastack.outbox.handler.OutboxRecordMetadata;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickOutboxHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @OutboxHandler
    public void handle(MarketTick tick, OutboxRecordMetadata metadata) {
        try {
            kafkaTemplate
                    .send(KafkaTopics.MARKET_TICKS, metadata.getKey(), tick)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to publish MarketTick for {}: {}", tick.symbol(), e.getMessage());
            throw new RuntimeException("Kafka send failed for tick " + tick.symbol(), e);
        }
    }
}
