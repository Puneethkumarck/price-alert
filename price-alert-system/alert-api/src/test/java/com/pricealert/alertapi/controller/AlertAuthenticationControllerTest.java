package com.pricealert.alertapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class AlertAuthenticationControllerTest extends AlertControllerBaseTest {

    @SneakyThrows
    @Test
    void shouldReturn403WhenNoTokenProvided() {
        mockMvc.perform(get(ALERTS_PATH)).andExpect(status().isForbidden());
    }

    @SneakyThrows
    @Test
    void shouldReturn403WhenInvalidTokenProvided() {
        mockMvc.perform(get(ALERTS_PATH).header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @SneakyThrows
    @Test
    void shouldAllowActuatorWithoutToken() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
