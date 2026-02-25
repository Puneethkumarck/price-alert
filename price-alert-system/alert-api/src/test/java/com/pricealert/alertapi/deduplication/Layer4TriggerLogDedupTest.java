package com.pricealert.alertapi.deduplication;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Layer4TriggerLogDedupTest extends DeduplicationBaseTest {

    @Test
    void shouldInsertFirstTriggerLog() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

        insertTriggerLogIdempotent(alert, LocalDate.now(), new BigDecimal("155.50"));

        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldSkipDuplicateTriggerLogForSameAlertAndTradingDate() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var tradingDate = LocalDate.now();

        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
        assertThat(triggerLogJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(new BigDecimal("155.50"));
    }

    @Test
    void shouldAllowTriggerLogsForSameAlertOnDifferentDays() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        insertTriggerLogIdempotent(alert, today, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert, today.minusDays(1), new BigDecimal("148.00"));

        assertThat(triggerLogJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldAllowTriggerLogsForDifferentAlertsOnSameDay() {
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        insertTriggerLogIdempotent(alert1, today, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert2, today, new BigDecimal("210.00"));

        assertThat(triggerLogJpaRepository.count()).isEqualTo(2);
    }
}
