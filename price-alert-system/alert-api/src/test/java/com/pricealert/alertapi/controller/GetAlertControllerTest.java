package com.pricealert.alertapi.controller;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetAlertControllerTest extends AlertControllerBaseTest {

    @Test
    void shouldReturnAlertById() throws Exception {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(get(ALERTS_PATH + "/" + entity.getId())
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(entity.getId())))
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.userId", is(USER_ID)));
    }

    @Test
    void shouldReturn404ForNonExistentAlert() throws Exception {
        mockMvc.perform(get(ALERTS_PATH + "/nonexistent_id")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Alert Not Found")));
    }

    @Test
    void shouldReturn404WhenAlertBelongsToAnotherUser() throws Exception {
        var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(get(ALERTS_PATH + "/" + entity.getId())
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }
}
