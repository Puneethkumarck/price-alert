package com.pricealert.alertapi.controller;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListAlertsControllerTest extends AlertControllerBaseTest {

    @Test
    void shouldReturnPaginatedAlerts() throws Exception {
        createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);
        createAlertEntity("GOOG", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(get(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void shouldFilterByStatus() throws Exception {
        createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);

        mockMvc.perform(get(ALERTS_PATH + "?status=ACTIVE")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].symbol", is("AAPL")));
    }

    @Test
    void shouldFilterBySymbol() throws Exception {
        createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(get(ALERTS_PATH + "?symbol=TSLA")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].symbol", is("TSLA")));
    }

    @Test
    void shouldReturnEmptyPageForNoResults() throws Exception {
        mockMvc.perform(get(ALERTS_PATH)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void shouldFilterByBothStatusAndSymbol() throws Exception {
        createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(get(ALERTS_PATH + "?status=ACTIVE&symbol=AAPL")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].symbol", is("AAPL")))
                .andExpect(jsonPath("$.content[0].status", is("ACTIVE")));
    }
}
