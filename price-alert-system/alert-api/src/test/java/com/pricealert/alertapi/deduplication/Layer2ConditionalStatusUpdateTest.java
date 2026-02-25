package com.pricealert.alertapi.deduplication;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Layer2ConditionalStatusUpdateTest extends DeduplicationBaseTest {

    @Test
    void shouldUpdateActiveToTriggeredToday() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

        var updated = jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE id = ? AND status = 'ACTIVE'",
                alert.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(alertJpaRepository.findById(alert.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
    }

    @Test
    void shouldSkipUpdateWhenAlreadyTriggeredToday() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

        var updated = jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                alert.getId());

        assertThat(updated).isZero();
    }

    @Test
    void shouldNotUpdateDeletedAlerts() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.DELETED);

        var updated = jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                alert.getId());

        assertThat(updated).isZero();
    }

    @Test
    void shouldOnlyUpdateActiveAlertAmongMixed() {
        var active = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
        var triggered = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var deleted = createAlertEntity("GOOG", USER_ID, AlertStatus.DELETED);

        var updated = jdbcTemplate.update(
                "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE status = 'ACTIVE'");
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(alertJpaRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
        assertThat(alertJpaRepository.findById(triggered.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.TRIGGERED_TODAY);
        assertThat(alertJpaRepository.findById(deleted.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.DELETED);
    }
}
