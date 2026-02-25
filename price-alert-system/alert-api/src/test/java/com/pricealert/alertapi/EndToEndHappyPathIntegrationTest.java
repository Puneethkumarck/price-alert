package com.pricealert.alertapi;

import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationEntity;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationJpaRepository;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogEntity;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import com.pricealert.common.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: E2E happy path.
 * Tests the complete flow within the alert-api context:
 * 1. Create alert via REST API → verify DB + Kafka event
 * 2. Simulate notification persistence (as notification-persister would) → verify via notifications API
 * 3. Alert status lifecycle (ACTIVE → TRIGGERED_TODAY)
 */
class EndToEndHappyPathIntegrationTest extends BaseIntegrationTest {

    private static final String ALERTS_PATH = "/api/v1/alerts";
    private static final String NOTIFICATIONS_PATH = "/api/v1/notifications";
    private static final String USER_ID = "user_e2e_001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertJpaRepository alertJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private AlertTriggerLogJpaRepository triggerLogJpaRepository;

    private KafkaConsumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUp() {
        triggerLogJpaRepository.deleteAll();
        notificationJpaRepository.deleteAll();
        alertJpaRepository.deleteAll();

        kafkaConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
    }

    @AfterEach
    void tearDown() {
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
    }

    @Nested
    class AlertCreationToKafka {

        @Test
        void shouldCreateAlertAndPublishCreatedEventToKafka() throws Exception {
            // given
            kafkaConsumer.subscribe(List.of(KafkaTopics.ALERT_CHANGES));
            // drain any pre-existing events from previous tests
            pollForRecords(kafkaConsumer, 3);

            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

            // when — create alert via REST API
            var result = mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "symbol": "AAPL",
                                        "thresholdPrice": 150.00,
                                        "direction": "ABOVE",
                                        "note": "E2E test alert"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.symbol").value("AAPL"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn();

            // then — alert is persisted in DB
            var alerts = alertJpaRepository.findBySymbolAndStatus("AAPL", AlertStatus.ACTIVE);
            assertThat(alerts).hasSize(1);
            var alert = alerts.getFirst();
            assertThat(alert.getUserId()).isEqualTo(USER_ID);
            assertThat(alert.getThresholdPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(alert.getDirection()).isEqualTo(Direction.ABOVE);
            assertThat(alert.getNote()).isEqualTo("E2E test alert");

            // then — CREATED event is on alert-changes topic
            // Accumulate records across multiple poll attempts — the outbox publishes asynchronously
            // so the record may not appear in the first batch.
            var matchingRecord = pollForMatchingRecord(kafkaConsumer, alert.getId(), 20)
                    .orElseThrow(() -> new AssertionError("No Kafka record found for alert " + alert.getId()));

            assertThat(matchingRecord.key()).isEqualTo("AAPL");
            assertThat(matchingRecord.value()).contains("\"event_type\":\"CREATED\"");
            assertThat(matchingRecord.value()).contains("\"user_id\":\"" + USER_ID + "\"");
            assertThat(matchingRecord.value()).contains("\"symbol\":\"AAPL\"");
            assertThat(matchingRecord.value()).contains("\"direction\":\"ABOVE\"");
        }

        @Test
        void shouldCreateMultipleAlertsForDifferentSymbols() throws Exception {
            // given
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

            // when — create AAPL alert
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"symbol": "AAPL", "thresholdPrice": 150.00, "direction": "ABOVE"}
                                    """))
                    .andExpect(status().isCreated());

            // when — create TSLA alert
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"symbol": "TSLA", "thresholdPrice": 200.00, "direction": "BELOW"}
                                    """))
                    .andExpect(status().isCreated());

            // then — both alerts exist in DB
            var allAlerts = alertJpaRepository.findByStatus(AlertStatus.ACTIVE);
            assertThat(allAlerts).hasSize(2);
            assertThat(allAlerts).extracting(AlertEntity::getSymbol)
                    .containsExactlyInAnyOrder("AAPL", "TSLA");
        }
    }

    @Nested
    class NotificationRetrieval {

        @Test
        void shouldReturnNotificationAfterPersistence() throws Exception {
            // given — create an alert
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

            // given — simulate notification persistence (as notification-persister would do)
            var notification = createNotificationEntity(alert, new BigDecimal("155.50"));

            // when — query notifications via API
            mockMvc.perform(get(NOTIFICATIONS_PATH)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                    .andExpect(jsonPath("$.content[0].alertId").value(alert.getId()));
        }

        @Test
        void shouldReturnMultipleNotificationsSortedByCreatedAtDesc() throws Exception {
            // given
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
            var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);

            // older notification
            var now = Instant.now();
            createNotificationEntityWithTimestamp(alert1, new BigDecimal("155.50"), now.minusSeconds(60));
            // newer notification
            createNotificationEntityWithTimestamp(alert2, new BigDecimal("210.00"), now);

            // when
            mockMvc.perform(get(NOTIFICATIONS_PATH)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].symbol").value("TSLA"))
                    .andExpect(jsonPath("$.content[1].symbol").value("AAPL"));
        }

        @Test
        void shouldNotReturnNotificationsOfOtherUsers() throws Exception {
            // given
            var otherUserId = "user_other_001";
            var alert = createAlertEntity("AAPL", otherUserId, AlertStatus.TRIGGERED_TODAY);
            createNotificationEntity(alert, new BigDecimal("155.50"));

            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

            // when
            mockMvc.perform(get(NOTIFICATIONS_PATH)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }

    @Nested
    class AlertStatusLifecycle {

        @Test
        void shouldReflectTriggeredTodayStatusInAlertGet() throws Exception {
            // given — create alert via API
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
            var createResult = mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"symbol": "AAPL", "thresholdPrice": 150.00, "direction": "ABOVE"}
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn();

            // extract alert ID from response
            var responseBody = createResult.getResponse().getContentAsString();
            var alertId = extractJsonField(responseBody, "id");

            // when — simulate evaluator marking alert as TRIGGERED_TODAY
            var alert = alertJpaRepository.findById(alertId).orElseThrow();
            alert.setStatus(AlertStatus.TRIGGERED_TODAY);
            alert.setUpdatedAt(Instant.now());
            alertJpaRepository.save(alert);

            // then — GET returns TRIGGERED_TODAY status
            mockMvc.perform(get(ALERTS_PATH + "/" + alertId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("TRIGGERED_TODAY"));
        }

        @Test
        void shouldShowDeletedAlertAsDeleted() throws Exception {
            // given — create and delete
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
            var createResult = mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"symbol": "GOOG", "thresholdPrice": 100.00, "direction": "BELOW"}
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn();

            var alertId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

            // when — soft-delete
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(ALERTS_PATH + "/" + alertId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            // then — alert in DB has DELETED status
            var deleted = alertJpaRepository.findById(alertId).orElseThrow();
            assertThat(deleted.getStatus()).isEqualTo(AlertStatus.DELETED);
        }
    }

    @Nested
    class FullHappyPath {

        @Test
        void shouldCompleteEntireFlowFromAlertCreationToNotificationRetrieval() throws Exception {
            // Step 1: Create alert via REST API
            var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
            var createResult = mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "symbol": "MSFT",
                                        "thresholdPrice": 400.00,
                                        "direction": "ABOVE",
                                        "note": "Full happy path test"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn();

            var alertId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

            // Step 2: Verify alert exists with ACTIVE status
            var alert = alertJpaRepository.findById(alertId).orElseThrow();
            assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
            assertThat(alert.getSymbol()).isEqualTo("MSFT");

            // Step 3: Simulate evaluator → mark alert TRIGGERED_TODAY
            alert.setStatus(AlertStatus.TRIGGERED_TODAY);
            alert.setUpdatedAt(Instant.now());
            alertJpaRepository.save(alert);

            // Step 4: Simulate notification-persister → insert notification + trigger_log
            var triggerPrice = new BigDecimal("405.25");
            var tradingDate = LocalDate.now();

            var notification = NotificationEntity.builder()
                    .id("notif_" + System.nanoTime())
                    .alertTriggerId("trig_" + System.nanoTime())
                    .alertId(alertId)
                    .userId(USER_ID)
                    .symbol("MSFT")
                    .thresholdPrice(new BigDecimal("400.00"))
                    .triggerPrice(triggerPrice)
                    .direction(Direction.ABOVE)
                    .note("Full happy path test")
                    .idempotencyKey(alertId + ":" + tradingDate)
                    .createdAt(Instant.now())
                    .read(false)
                    .build();
            notificationJpaRepository.save(notification);

            var triggerLog = AlertTriggerLogEntity.builder()
                    .id("log_" + System.nanoTime())
                    .alertId(alertId)
                    .userId(USER_ID)
                    .symbol("MSFT")
                    .thresholdPrice(new BigDecimal("400.00"))
                    .triggerPrice(triggerPrice)
                    .tickTimestamp(Instant.now())
                    .triggeredAt(Instant.now())
                    .tradingDate(tradingDate)
                    .build();
            triggerLogJpaRepository.save(triggerLog);

            // Step 5: Verify alert shows TRIGGERED_TODAY via API
            mockMvc.perform(get(ALERTS_PATH + "/" + alertId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("TRIGGERED_TODAY"));

            // Step 6: Verify notification appears in notifications API
            mockMvc.perform(get(NOTIFICATIONS_PATH)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].alertId").value(alertId))
                    .andExpect(jsonPath("$.content[0].symbol").value("MSFT"));

            // Step 7: Verify trigger_log exists
            var triggerLogs = triggerLogJpaRepository.findAll();
            assertThat(triggerLogs).hasSize(1);
            assertThat(triggerLogs.getFirst().getAlertId()).isEqualTo(alertId);
            assertThat(triggerLogs.getFirst().getTradingDate()).isEqualTo(tradingDate);
        }
    }

    // --- Helper methods ---

    private AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        var entity = AlertEntity.builder()
                .id("alt_" + System.nanoTime())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(status)
                .note("Test alert")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return alertJpaRepository.save(entity);
    }

    private NotificationEntity createNotificationEntity(AlertEntity alert, BigDecimal triggerPrice) {
        return createNotificationEntityWithTimestamp(alert, triggerPrice, Instant.now());
    }

    private NotificationEntity createNotificationEntityWithTimestamp(AlertEntity alert, BigDecimal triggerPrice, Instant createdAt) {
        var entity = NotificationEntity.builder()
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
                .build();
        return notificationJpaRepository.save(entity);
    }

    private ConsumerRecords<String, String> pollForRecords(KafkaConsumer<String, String> consumer, int maxAttempts) {
        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        for (int i = 0; i < maxAttempts; i++) {
            records = consumer.poll(Duration.ofSeconds(1));
            if (!records.isEmpty()) {
                return records;
            }
        }
        return records;
    }

    /**
     * Polls across multiple batches accumulating all records until a record matching
     * the given alertId is found, or maxAttempts is exhausted.
     */
    private Optional<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> pollForMatchingRecord(
            KafkaConsumer<String, String> consumer, String alertId, int maxAttempts) {
        var allRecords = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
        for (int i = 0; i < maxAttempts; i++) {
            var batch = consumer.poll(Duration.ofSeconds(1));
            batch.forEach(allRecords::add);
            var match = allRecords.stream()
                    .filter(r -> r.value().contains("\"alert_id\":\"" + alertId + "\""))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private String extractJsonField(String json, String field) {
        // Simple JSON field extraction for test purposes
        var pattern = "\"" + field + "\":\"";
        var start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        var end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
