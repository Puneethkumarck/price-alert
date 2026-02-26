package com.pricealert.alertapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.common.event.AlertStatus;
import java.math.BigDecimal;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UpdateAlertControllerTest extends AlertControllerBaseTest {

    @SneakyThrows
    @Test
    void shouldUpdateThresholdPrice() {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        patch(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"thresholdPrice": 175.00}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdPrice").value(175.00))
                .andExpect(jsonPath("$.direction", is("ABOVE")));

        var updated = alertJpaRepository.findById(entity.getId()).orElseThrow();
        assertThat(updated.getThresholdPrice()).isEqualByComparingTo(new BigDecimal("175.00"));
    }

    @SneakyThrows
    @Test
    void shouldUpdateDirection() {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        patch(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"direction": "BELOW"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction", is("BELOW")));
    }

    @SneakyThrows
    @Test
    void shouldUpdateNote() {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        patch(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"note": "Updated note"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note", is("Updated note")));
    }

    @SneakyThrows
    @Test
    void shouldReturn404WhenUpdatingNonExistentAlert() {
        mockMvc.perform(
                        patch(ALERTS_PATH + "/nonexistent_id")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"thresholdPrice": 175.00}
                                        """))
                .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    void shouldReturn404WhenUpdatingOtherUsersAlert() {
        var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        patch(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"thresholdPrice": 175.00}
                                        """))
                .andExpect(status().isNotFound());
    }
}
