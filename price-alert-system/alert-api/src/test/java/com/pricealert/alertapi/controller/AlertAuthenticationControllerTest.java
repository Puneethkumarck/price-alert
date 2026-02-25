package com.pricealert.alertapi.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertAuthenticationControllerTest extends AlertControllerBaseTest {

    @Test
    void shouldReturn403WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get(ALERTS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403WhenInvalidTokenProvided() throws Exception {
        mockMvc.perform(get(ALERTS_PATH)
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowActuatorWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
