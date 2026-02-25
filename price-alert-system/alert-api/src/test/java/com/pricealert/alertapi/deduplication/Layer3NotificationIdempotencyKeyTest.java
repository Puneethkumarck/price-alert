package com.pricealert.alertapi.deduplication;

import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Layer3NotificationIdempotencyKeyTest extends DeduplicationBaseTest {

    @Test
    void shouldInsertFirstNotification() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var idempotencyKey = alert.getId() + ":" + LocalDate.now();

        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("155.50"));

        assertThat(notificationJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldSkipDuplicateNotificationWithSameIdempotencyKey() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var idempotencyKey = alert.getId() + ":" + LocalDate.now();

        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("155.50"));
        insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));

        assertThat(notificationJpaRepository.count()).isEqualTo(1);
        assertThat(notificationJpaRepository.findAll().getFirst().getTriggerPrice())
                .isEqualByComparingTo(new BigDecimal("155.50"));
    }

    @Test
    void shouldAllowNotificationsWithDifferentIdempotencyKeys() {
        var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        insertNotificationIdempotent(alert, alert.getId() + ":" + today, new BigDecimal("155.50"));
        insertNotificationIdempotent(alert, alert.getId() + ":" + today.minusDays(1), new BigDecimal("148.00"));

        assertThat(notificationJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldAllowNotificationsForDifferentAlertsOnSameDay() {
        var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
        var today = LocalDate.now();

        insertNotificationIdempotent(alert1, alert1.getId() + ":" + today, new BigDecimal("155.50"));
        insertNotificationIdempotent(alert2, alert2.getId() + ":" + today, new BigDecimal("210.00"));

        assertThat(notificationJpaRepository.count()).isEqualTo(2);
    }
}
