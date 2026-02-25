package com.pricealert.alertapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateAlertControllerTest extends AlertControllerBaseTest {

    @Test
    void shouldCreateAlertAndReturn201() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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

    @Test
    void shouldRejectInvalidSymbol() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "invalid123",
                                    "thresholdPrice": 150.50,
                                    "direction": "ABOVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    void shouldRejectMissingThresholdPrice() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "direction": "ABOVE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMissingDirection() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "thresholdPrice": 150.50
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectZeroThresholdPrice() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "thresholdPrice": 0,
                                    "direction": "ABOVE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateAlertWithNullNote() throws Exception {
        mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
