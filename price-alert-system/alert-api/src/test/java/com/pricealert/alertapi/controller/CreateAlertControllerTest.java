package com.pricealert.alertapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CreateAlertControllerTest extends AlertControllerBaseTest {

    @SneakyThrows
    @Test
    void shouldCreateAlertAndReturn201() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "AAPL",
                                            "thresholdPrice": 150.50,
                                            "direction": "ABOVE",
                                            "note": "Buy signal"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is(USER_ID)))
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.thresholdPrice").value(150.50))
                .andExpect(jsonPath("$.direction", is("ABOVE")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.note", is("Buy signal")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

        assertThat(alertJpaRepository.count()).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    void shouldRejectInvalidSymbol() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "invalid123",
                                            "thresholdPrice": 150.50,
                                            "direction": "ABOVE"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @SneakyThrows
    @Test
    void shouldRejectMissingThresholdPrice() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "AAPL",
                                            "direction": "ABOVE"
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @SneakyThrows
    @Test
    void shouldRejectMissingDirection() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "AAPL",
                                            "thresholdPrice": 150.50
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @SneakyThrows
    @Test
    void shouldRejectZeroThresholdPrice() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "AAPL",
                                            "thresholdPrice": 0,
                                            "direction": "ABOVE"
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @SneakyThrows
    @Test
    void shouldCreateAlertWithNullNote() {
        mockMvc.perform(
                        post(ALERTS_PATH)
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                            "symbol": "TSLA",
                                            "thresholdPrice": 200.00,
                                            "direction": "BELOW"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("TSLA")))
                .andExpect(jsonPath("$.direction", is("BELOW")));
    }
}
