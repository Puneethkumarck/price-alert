package com.pricealert.alertapi.e2e;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationRetrievalTest extends E2EBaseTest {

    @Test
    void shouldReturnNotificationAfterPersistence() throws Exception {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        createNotificationEntity(alert, new BigDecimal("155.50"));

        mockMvc.perform(MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].alertId").value(alert.getId()));
    }

    @Test
    void shouldReturnMultipleNotificationsSortedByCreatedAtDesc() throws Exception {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);

        var now = Instant.now();
        createNotificationEntityWithTimestamp(alert1, new BigDecimal("155.50"), now.minusSeconds(60));
        createNotificationEntityWithTimestamp(alert2, new BigDecimal("210.00"), now);

        mockMvc.perform(MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].symbol").value("TSLA"))
                .andExpect(jsonPath("$.content[1].symbol").value("AAPL"));
    }

    @Test
    void shouldNotReturnNotificationsOfOtherUsers() throws Exception {
        var otherUserId = "user_other_001";
        var alert = createAlertEntity("AAPL", otherUserId, AlertStatus.TRIGGERED_TODAY);
        createNotificationEntity(alert, new BigDecimal("155.50"));

        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);

        mockMvc.perform(MockMvcRequestBuilders.get(NOTIFICATIONS_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
