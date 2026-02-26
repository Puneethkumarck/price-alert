package com.pricealert.alertapi.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import com.pricealert.common.kafka.KafkaTopics;
import java.math.BigDecimal;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AlertCreationToKafkaTest extends E2EBaseTest {

    @SneakyThrows
    @Test
    void shouldCreateAlertAndPublishCreatedEventToKafka() {
        kafkaConsumer.subscribe(List.of(KafkaTopics.ALERT_CHANGES));
        drainTopic(kafkaConsumer);

        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "AAPL",
                                            "thresholdPrice": 150.00,
                                            "direction": "ABOVE",
                                            "note": "E2E test alert"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        var alerts = alertJpaRepository.findBySymbolAndStatus("AAPL", AlertStatus.ACTIVE);
        assertThat(alerts).hasSize(1);
        var alert = alerts.getFirst();
        assertThat(alert)
                .satisfies(
                        a -> {
                            assertThat(a.getUserId()).isEqualTo(USER_ID);
                            assertThat(a.getThresholdPrice())
                                    .isEqualByComparingTo(new BigDecimal("150.00"));
                            assertThat(a.getDirection()).isEqualTo(Direction.ABOVE);
                            assertThat(a.getNote()).isEqualTo("E2E test alert");
                        });

        var matchingRecord =
                pollForMatchingRecord(kafkaConsumer, alert.getId(), 20)
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "No Kafka record found for alert "
                                                        + alert.getId()));

        assertThat(matchingRecord.key()).isEqualTo("AAPL");
        assertThat(matchingRecord.value()).contains("\"event_type\":\"CREATED\"");
        assertThat(matchingRecord.value()).contains("\"user_id\":\"" + USER_ID + "\"");
        assertThat(matchingRecord.value()).contains("\"symbol\":\"AAPL\"");
        assertThat(matchingRecord.value()).contains("\"direction\":\"ABOVE\"");
    }

    @SneakyThrows
    @Test
    void shouldCreateMultipleAlertsForDifferentSymbols() {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"symbol": "AAPL", "thresholdPrice": 150.00, "direction": "ABOVE"}
                                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"symbol": "TSLA", "thresholdPrice": 200.00, "direction": "BELOW"}
                                        """))
                .andExpect(status().isCreated());

        var allAlerts = alertJpaRepository.findByStatus(AlertStatus.ACTIVE);

        assertThat(allAlerts)
                .extracting(AlertEntity::getSymbol)
                .containsExactlyInAnyOrder("AAPL", "TSLA");
    }
}
