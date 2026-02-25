package com.pricealert.alertapi.controller;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeleteAlertControllerTest extends AlertControllerBaseTest {

    @Test
    void shouldSoftDeleteAlertAndReturn204() throws Exception {
        var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(delete(ALERTS_PATH + "/" + entity.getId())
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNoContent());

        var deleted = alertJpaRepository.findById(entity.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(AlertStatus.DELETED);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentAlert() throws Exception {
        mockMvc.perform(delete(ALERTS_PATH + "/nonexistent_id")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenDeletingOtherUsersAlert() throws Exception {
        var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

        mockMvc.perform(delete(ALERTS_PATH + "/" + entity.getId())
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }
}
