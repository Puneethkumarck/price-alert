package com.pricealert.alertapi.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.common.event.AlertStatus;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class GetAlertControllerTest extends AlertControllerBaseTest {

    @SneakyThrows
    @Test
    void shouldReturnAlertById() {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        get(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(entity.getId())))
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.userId", is(USER_ID)));
    }

    @SneakyThrows
    @Test
    void shouldReturn404ForNonExistentAlert() {
        mockMvc.perform(
                        get(ALERTS_PATH + "/nonexistent_id")
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Alert Not Found")));
    }

    @SneakyThrows
    @Test
    void shouldReturn404WhenAlertBelongsToAnotherUser() {
        var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        get(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }
}
