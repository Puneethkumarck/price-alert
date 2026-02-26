package com.pricealert.alertapi.deduplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

class Layer2ConditionalStatusUpdateTest extends DeduplicationBaseTest {

    @Test
    void shouldUpdateActiveAlertToTriggeredToday() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        // when
        var updated =
                jdbcTemplate.update(
                        "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE id"
                                + " = ? AND status = 'ACTIVE'",
                        alert.getId());
        entityManager.clear();

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(alertJpaRepository.findById(alert.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
    }

    @Test
    void shouldSkipUpdateWhenAlreadyTriggeredToday() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

        // when
        var updated =
                jdbcTemplate.update(
                        "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status ="
                                + " 'ACTIVE'",
                        alert.getId());

        // then
        assertThat(updated).isZero();
    }

    @Test
    void shouldNotUpdateDeletedAlerts() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.DELETED);

        // when
        var updated =
                jdbcTemplate.update(
                        "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status ="
                                + " 'ACTIVE'",
                        alert.getId());

        // then
        assertThat(updated).isZero();
    }

    @Test
    void shouldOnlyUpdateActiveAlertAmongMixedStatuses() {
        // given
        var active = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        var triggered = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var deleted = createAlertEntity("GOOG", USER_ID, AlertStatus.DELETED);

        // when
        var updated =
                jdbcTemplate.update(
                        "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE"
                                + " status = 'ACTIVE'");
        entityManager.clear();

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(alertJpaRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
        assertThat(alertJpaRepository.findById(triggered.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
        assertThat(alertJpaRepository.findById(deleted.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.DELETED);
    }
}
