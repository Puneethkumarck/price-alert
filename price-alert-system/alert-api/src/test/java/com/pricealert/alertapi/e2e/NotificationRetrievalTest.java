package com.pricealert.alertapi.e2e;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.common.event.AlertStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class NotificationRetrievalTest extends E2EBaseTest {

    @SneakyThrows
    @Test
    void shouldReturnNotificationAfterPersistence() {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        createNotificationEntity(alert, new BigDecimal("155.50"));

        mockMvc.perform(
                        MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].alertId").value(alert.getId()));
    }

    @SneakyThrows
    @Test
    void shouldReturnMultipleNotificationsSortedByCreatedAtDesc() {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);

        var now = Instant.now();
        createNotificationEntityWithTimestamp(
                alert1, new BigDecimal("155.50"), now.minusSeconds(60));
        createNotificationEntityWithTimestamp(alert2, new BigDecimal("210.00"), now);

        mockMvc.perform(
                        MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].symbol").value("TSLA"))
                .andExpect(jsonPath("$.content[1].symbol").value("AAPL"));
    }

    @SneakyThrows
    @Test
    void shouldNotReturnNotificationsOfOtherUsers() {
        var otherUserId = "user_other_001";
        var alert = createAlertEntity("AAPL", otherUserId, AlertStatus.TRIGGERED_TODAY);
        createNotificationEntity(alert, new BigDecimal("155.50"));

        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(
                        MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
