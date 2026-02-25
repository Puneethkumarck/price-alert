package com.pricealert.alertapi.e2e;

import com.pricealert.alertapi.BaseIntegrationTest;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationEntity;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationJpaRepository;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public abstract class E2EBaseTest extends BaseIntegrationTest {

    static final String ALERTS_PATH = "/api/v1/alerts";
    static final String NOTIFICATIONS_PATH = "/api/v1/notifications";
    static final String USER_ID = "user_e2e_001";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlertJpaRepository alertJpaRepository;

    @Autowired
    NotificationJpaRepository notificationJpaRepository;

    @Autowired
    AlertTriggerLogJpaRepository triggerLogJpaRepository;

    KafkaConsumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUpKafkaConsumer() {
        kafkaConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
    }

    @AfterEach
    void closeKafkaConsumer() {
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
    }

    AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        return alertJpaRepository.save(AlertEntity.builder()
                .id("alt_" + System.nanoTime())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(status)
                .note("Test alert")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    NotificationEntity createNotificationEntity(AlertEntity alert, BigDecimal triggerPrice) {
        return createNotificationEntityWithTimestamp(alert, triggerPrice, Instant.now());
    }

    NotificationEntity createNotificationEntityWithTimestamp(AlertEntity alert, BigDecimal triggerPrice, Instant createdAt) {
        return notificationJpaRepository.save(NotificationEntity.builder()
                .id("notif_" + System.nanoTime())
                .alertTriggerId("trig_" + System.nanoTime())
                .alertId(alert.getId())
                .userId(alert.getUserId())
                .symbol(alert.getSymbol())
                .thresholdPrice(alert.getThresholdPrice())
                .triggerPrice(triggerPrice)
                .direction(alert.getDirection())
                .note(alert.getNote())
                .idempotencyKey(alert.getId() + ":" + LocalDate.now() + "_" + System.nanoTime())
                .createdAt(createdAt)
                .read(false)
                .build());
    }

    void drainTopic(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> batch;
        do {
            batch = consumer.poll(Duration.ofMillis(500));
        } while (!batch.isEmpty());
    }

    Optional<ConsumerRecord<String, String>> pollForMatchingRecord(
            KafkaConsumer<String, String> consumer, String alertId, int maxAttempts) {
        var allRecords = new ArrayList<ConsumerRecord<String, String>>();
        for (int i = 0; i < maxAttempts; i++) {
            consumer.poll(Duration.ofSeconds(1)).forEach(allRecords::add);
            var match = allRecords.stream()
                    .filter(r -> r.value().contains("\"alert_id\":\"" + alertId + "\""))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    String extractJsonField(String json, String field) {
        var pattern = "\"" + field + "\":\"";
        var start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        return json.substring(start, json.indexOf("\"", start));
    }
}
