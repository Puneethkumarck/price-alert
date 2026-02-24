package com.pricealert.alertapi;

import com.pricealert.alertapi.application.job.DailyResetScheduler;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DailyResetSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DailyResetScheduler dailyResetScheduler;

    @Autowired
    private AlertJpaRepository alertJpaRepository;

    @BeforeEach
    void setUp() {
        alertJpaRepository.deleteAll();
    }

    @Nested
    class ResetTriggeredAlerts {

        @Test
        void shouldResetTriggeredTodayAlertsToActive() {
            // given
            var triggered1 = createAlertEntity("AAPL", "user1", AlertStatus.TRIGGERED_TODAY);
            var triggered2 = createAlertEntity("TSLA", "user1", AlertStatus.TRIGGERED_TODAY);
            var active = createAlertEntity("GOOG", "user1", AlertStatus.ACTIVE);
            var deleted = createAlertEntity("MSFT", "user1", AlertStatus.DELETED);

            // when
            dailyResetScheduler.resetTriggeredAlerts();

            // then — TRIGGERED_TODAY → ACTIVE
            assertThat(alertJpaRepository.findById(triggered1.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
            assertThat(alertJpaRepository.findById(triggered2.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);

            // unchanged
            assertThat(alertJpaRepository.findById(active.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
            assertThat(alertJpaRepository.findById(deleted.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.DELETED);
        }

        @Test
        void shouldBeNoOpWhenNoTriggeredAlerts() {
            // given
            var active = createAlertEntity("AAPL", "user1", AlertStatus.ACTIVE);

            // when
            dailyResetScheduler.resetTriggeredAlerts();

            // then — unchanged
            assertThat(alertJpaRepository.findById(active.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
        }

        @Test
        void shouldResetAlertsFromMultipleUsers() {
            // given
            var user1Alert = createAlertEntity("AAPL", "user1", AlertStatus.TRIGGERED_TODAY);
            var user2Alert = createAlertEntity("TSLA", "user2", AlertStatus.TRIGGERED_TODAY);

            // when
            dailyResetScheduler.resetTriggeredAlerts();

            // then
            assertThat(alertJpaRepository.findById(user1Alert.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
            assertThat(alertJpaRepository.findById(user2Alert.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
        }

        @Test
        void shouldBeIdempotentWhenRunTwice() {
            // given
            var triggered = createAlertEntity("AAPL", "user1", AlertStatus.TRIGGERED_TODAY);

            // when — run twice
            dailyResetScheduler.resetTriggeredAlerts();
            dailyResetScheduler.resetTriggeredAlerts();

            // then — still ACTIVE, no error
            assertThat(alertJpaRepository.findById(triggered.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.ACTIVE);
        }
    }

    private AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        var entity = AlertEntity.builder()
                .id("alt_" + System.nanoTime())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(status)
                .note("Test alert")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return alertJpaRepository.save(entity);
    }
}
