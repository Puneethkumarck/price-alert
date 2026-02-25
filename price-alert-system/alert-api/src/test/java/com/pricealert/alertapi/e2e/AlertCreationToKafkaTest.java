package com.pricealert.alertapi.e2e;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import com.pricealert.common.kafka.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertCreationToKafkaTest extends E2EBaseTest {

    @Test
    void shouldCreateAlertAndPublishCreatedEventToKafka() throws Exception {
        kafkaConsumer.subscribe(List.of(KafkaTopics.ALERT_CHANGES));
        drainTopic(kafkaConsumer);

        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(post(ALERTS_PATH)
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
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        var alerts = alertJpaRepository.findBySymbolAndStatus("AAPL", AlertStatus.ACTIVE);
        assertThat(alerts).hasSize(1);
        var alert = alerts.getFirst();
        assertThat(alert.getUserId()).isEqualTo(USER_ID);
        assertThat(alert.getThresholdPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(alert.getDirection()).isEqualTo(Direction.ABOVE);
        assertThat(alert.getNote()).isEqualTo("E2E test alert");

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
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol": "AAPL", "thresholdPrice": 150.00, "direction": "ABOVE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol": "TSLA", "thresholdPrice": 200.00, "direction": "BELOW"}
                                """))
                .andExpect(status().isCreated());

        var allAlerts = alertJpaRepository.findByStatus(AlertStatus.ACTIVE);
        assertThat(allAlerts).hasSize(2);
        assertThat(allAlerts).extracting(a -> a.getSymbol())
                .containsExactlyInAnyOrder("AAPL", "TSLA");
    }
}
