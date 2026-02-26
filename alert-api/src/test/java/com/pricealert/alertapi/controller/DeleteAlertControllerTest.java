package com.pricealert.alertapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.common.event.AlertStatus;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class DeleteAlertControllerTest extends AlertControllerBaseTest {

    @SneakyThrows
    @Test
    void shouldSoftDeleteAlertAndReturn204() {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        delete(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNoContent());

        var deleted = alertJpaRepository.findById(entity.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(AlertStatus.DELETED);
    }

    @SneakyThrows
    @Test
    void shouldReturn404WhenDeletingNonExistentAlert() {
        mockMvc.perform(
                        delete(ALERTS_PATH + "/nonexistent_id")
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    void shouldReturn404WhenDeletingOtherUsersAlert() {
        var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(
                        delete(ALERTS_PATH + "/" + entity.getId())
                                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }
}
