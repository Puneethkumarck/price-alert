package com.pricealert.alertapi.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.common.event.AlertStatus;
import java.time.Instant;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AlertStatusLifecycleTest extends E2EBaseTest {

    @SneakyThrows
    @Test
    void shouldReflectTriggeredTodayStatusInAlertGet() {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var createResult =
                mockMvc.perform(
                                post(ALERTS_PATH)
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"symbol": "AAPL", "thresholdPrice": 150.00, "direction": "ABOVE"}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn();

        var alertId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        var alert = alertJpaRepository.findById(alertId).orElseThrow();
        alert.setStatus(AlertStatus.TRIGGERED_TODAY);
        alert.setUpdatedAt(Instant.now());
        alertJpaRepository.save(alert);

        mockMvc.perform(get(ALERTS_PATH + "/" + alertId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIGGERED_TODAY"));
    }

    @SneakyThrows
    @Test
    void shouldShowDeletedAlertAsDeleted() {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var createResult =
                mockMvc.perform(
                                post(ALERTS_PATH)
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"symbol": "GOOG", "thresholdPrice": 100.00, "direction": "BELOW"}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn();

        var alertId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(
                        delete(ALERTS_PATH + "/" + alertId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(alertJpaRepository.findById(alertId).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.DELETED);
    }
}
