package com.pricealert.alertapi.deduplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.AlertStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Layer4TriggerLogDedupTest extends DeduplicationBaseTest {

    @Test
    void shouldInsertFirstTriggerLog() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

        // when
        insertTriggerLogIdempotent(alert, LocalDate.now(), new BigDecimal("155.50"));

        // then
        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldSkipDuplicateTriggerLogForSameAlertAndTradingDate() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var tradingDate = LocalDate.now();

        // when
        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

        // then
        assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
        assertThat(triggerLogJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(new BigDecimal("155.50"));
    }

    @Test
    void shouldAllowTriggerLogsForSameAlertOnDifferentDays() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        // when
        insertTriggerLogIdempotent(alert, today, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert, today.minusDays(1), new BigDecimal("148.00"));

        // then
        assertThat(triggerLogJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldAllowTriggerLogsForDifferentAlertsOnSameDay() {
        // given
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        // when
        insertTriggerLogIdempotent(alert1, today, new BigDecimal("155.50"));
        insertTriggerLogIdempotent(alert2, today, new BigDecimal("210.00"));

        // then
        assertThat(triggerLogJpaRepository.count()).isEqualTo(2);
    }
}
