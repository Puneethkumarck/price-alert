package com.pricealert.alertapi.deduplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.AlertStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Layer3NotificationIdempotencyKeyTest extends DeduplicationBaseTest {

    @Test
    void shouldInsertFirstNotification() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var idempotencyKey = alert.getId() + ":" + LocalDate.now();

        // when
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("155.50"));

        // then
        assertThat(notificationJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldSkipDuplicateNotificationWithSameIdempotencyKey() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var idempotencyKey = alert.getId() + ":" + LocalDate.now();

        // when
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("155.50"));
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));

        // then
        assertThat(notificationJpaRepository.count()).isEqualTo(1);
        assertThat(notificationJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(new BigDecimal("155.50"));
    }

    @Test
    void shouldAllowNotificationsWithDifferentIdempotencyKeys() {
        // given
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        // when
        insertNotificationIdempotent(alert, alert.getId() + ":" + today, new BigDecimal("155.50"));
        insertNotificationIdempotent(
                alert, alert.getId() + ":" + today.minusDays(1), new BigDecimal("148.00"));

        // then
        assertThat(notificationJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldAllowNotificationsForDifferentAlertsOnSameDay() {
        // given
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        // when
        insertNotificationIdempotent(
                alert1, alert1.getId() + ":" + today, new BigDecimal("155.50"));
        insertNotificationIdempotent(
                alert2, alert2.getId() + ":" + today, new BigDecimal("210.00"));

        // then
        assertThat(notificationJpaRepository.count()).isEqualTo(2);
    }
}
