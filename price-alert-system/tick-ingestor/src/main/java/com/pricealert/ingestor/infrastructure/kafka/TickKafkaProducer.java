package com.pricealert.ingestor.infrastructure.kafka;

import com.pricealert.common.event.MarketTick;
import com.pricealert.common.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class TickKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TickKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String tickJson) {
        try {
            var node = objectMapper.readTree(tickJson);
            var type = node.path("type").asString("");

            if (!"TICK".equals(type)) {
                return;
            }

            var tick = objectMapper.readValue(tickJson, MarketTick.class);
            kafkaTemplate.send(KafkaTopics.MARKET_TICKS, tick.symbol(), tick);
        } catch (Exception e) {
            log.error("Failed to send tick to Kafka: {}", e.getMessage());
        }
    }
}
