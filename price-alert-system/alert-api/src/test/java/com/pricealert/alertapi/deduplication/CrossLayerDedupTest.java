package com.pricealert.alertapi.deduplication;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CrossLayerDedupTest extends DeduplicationBaseTest {

    @Test
    void shouldHandleFullDuplicateTriggerGracefully() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        var tradingDate = LocalDate.now();
        var triggerPrice = new BigDecimal("155.50");
        var idempotencyKey = alert.getId() + ":" + tradingDate;

        jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                alert.getId());
        insertNotificationIdempotent(alert, idempotencyKey, triggerPrice);
        insertTriggerLogIdempotent(alert, tradingDate, triggerPrice);

        assertThat(alertJpaRepository.findById(alert.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
        assertThat(notificationJpaRepository.count()).isEqualTo(1);
        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);

        var statusUpdate = jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                alert.getId());
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));
        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

        assertThat(statusUpdate).isZero();
        assertThat(notificationJpaRepository.count()).isEqualTo(1);
        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
        assertThat(notificationJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(triggerPrice);
    }
}
