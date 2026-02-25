package com.pricealert.alertapi.e2e;

import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationEntity;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogEntity;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FullHappyPathTest extends E2EBaseTest {

    @Test
    void shouldCompleteEntireFlowFromAlertCreationToNotificationRetrieval() throws Exception {
        var token = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        var createResult = mockMvc.perform(post(ALERTS_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "MSFT",
                                    "thresholdPrice": 400.00,
                                    "direction": "ABOVE",
                                    "note": "Full happy path test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var alertId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        var alert = alertJpaRepository.findById(alertId).orElseThrow();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(alert.getSymbol()).isEqualTo("MSFT");

        alert.setStatus(AlertStatus.TRIGGERED_TODAY);
        alert.setUpdatedAt(Instant.now());
        alertJpaRepository.save(alert);

        var triggerPrice = new BigDecimal("405.25");
        var tradingDate = LocalDate.now();

        notificationJpaRepository.save(NotificationEntity.builder()
                .id("notif_" + System.nanoTime())
                .alertTriggerId("trig_" + System.nanoTime())
                .alertId(alertId)
                .userId(USER_ID)
                .symbol("MSFT")
                .thresholdPrice(new BigDecimal("400.00"))
                .triggerPrice(triggerPrice)
                .direction(Direction.ABOVE)
                .note("Full happy path test")
                .idempotencyKey(alertId + ":" + tradingDate)
                .createdAt(Instant.now())
                .read(false)
                .build());

        triggerLogJpaRepository.save(AlertTriggerLogEntity.builder()
                .id("log_" + System.nanoTime())
                .alertId(alertId)
                .userId(USER_ID)
                .symbol("MSFT")
                .thresholdPrice(new BigDecimal("400.00"))
                .triggerPrice(triggerPrice)
                .tickTimestamp(Instant.now())
                .triggeredAt(Instant.now())
                .tradingDate(tradingDate)
                .build());

        mockMvc.perform(get(ALERTS_PATH + "/" + alertId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIGGERED_TODAY"));

        mockMvc.perform(get(NOTIFICATIONS_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].alertId").value(alertId))
                .andExpect(jsonPath("$.content[0].symbol").value("MSFT"));

        var triggerLogs = triggerLogJpaRepository.findAll();
        assertThat(triggerLogs).hasSize(1);
        assertThat(triggerLogs.getFirst().getAlertId()).isEqualTo(alertId);
        assertThat(triggerLogs.getFirst().getTradingDate()).isEqualTo(tradingDate);
    }
}
