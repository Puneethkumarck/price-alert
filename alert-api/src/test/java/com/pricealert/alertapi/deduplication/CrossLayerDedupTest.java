package com.pricealert.alertapi.deduplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.AlertStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CrossLayerDedupTest extends DeduplicationBaseTest {

    @Test
    void shouldHandleFullDuplicateTriggerGracefully() {
        // given
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

        // when — retry with duplicate data
        var statusUpdate =
                jdbcTemplate.update(
                        "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status ="
                                + " 'ACTIVE'",
                        alert.getId());
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));
        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

        // then — all layers absorbed the duplicate
        assertThat(statusUpdate).isZero();
        assertThat(notificationJpaRepository.count()).isEqualTo(1);
        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
        assertThat(notificationJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(triggerPrice);
    }
}
