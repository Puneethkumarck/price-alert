package com.pricealert.ingestor.infrastructure.kafka;

import com.pricealert.common.event.MarketTick;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickKafkaProducer {

    private final Outbox outbox;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Transactional
    public void send(String tickJson) {
        try {
            var node = objectMapper.readTree(tickJson);
            var type = node.path("type").asString("");

            if (!"TICK".equals(type)) {
                return;
            }

            var tick = objectMapper.readValue(tickJson, MarketTick.class);
            outbox.schedule(tick, tick.symbol());
        } catch (Exception e) {
            log.error("Failed to schedule tick to outbox: {}", e.getMessage());
        }
    }
}
