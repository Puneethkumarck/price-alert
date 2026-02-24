package com.pricealert.alertapi;

import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationEntity;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationJpaRepository;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogEntity;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: Deduplication across all 4 layers.
 * Layer 2: Conditional DB status update (ACTIVE → TRIGGERED_TODAY, skip if not ACTIVE)
 * Layer 3: notifications.idempotency_key UNIQUE constraint (ON CONFLICT DO NOTHING)
 * Layer 4: alert_trigger_log unique index on (alert_id, trading_date) (ON CONFLICT DO NOTHING)
 */
class DeduplicationIntegrationTest extends BaseIntegrationTest {

    private static final String USER_ID = "user_dedup_001";

    @Autowired
    private AlertJpaRepository alertJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private AlertTriggerLogJpaRepository triggerLogJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        triggerLogJpaRepository.deleteAll();
        notificationJpaRepository.deleteAll();
        alertJpaRepository.deleteAll();
    }

    @Nested
    class Layer2ConditionalStatusUpdate {

        @Test
        void shouldUpdateActiveToTriggeredToday() {
            // given
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

            // when — conditional update targeting ACTIVE alerts
            var updated = jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE id = ? AND status = 'ACTIVE'",
                    alert.getId());
            entityManager.clear(); // flush JPA cache so findById fetches fresh data

            // then
            assertThat(updated).isEqualTo(1);
            var reloaded = alertJpaRepository.findById(alert.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(AlertStatus.TRIGGERED_TODAY);
        }

        @Test
        void shouldSkipUpdateWhenAlreadyTriggeredToday() {
            // given — alert already TRIGGERED_TODAY
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);

            // when — conditional update targeting ACTIVE alerts
            var updated = jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                    alert.getId());

            // then — no rows affected (dedup: skip if not ACTIVE)
            assertThat(updated).isZero();
        }

        @Test
        void shouldNotUpdateDeletedAlerts() {
            // given
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.DELETED);

            // when
            var updated = jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                    alert.getId());

            // then
            assertThat(updated).isZero();
        }

        @Test
        void shouldOnlyUpdateActiveAlertAmongMixed() {
            // given — mixed statuses
            var active = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            var triggered = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var deleted = createAlertEntity("GOOG", USER_ID, AlertStatus.DELETED);

            // when — bulk conditional update targeting only ACTIVE alerts
            var updated = jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY', updated_at = now() WHERE status = 'ACTIVE'");
            entityManager.clear(); // flush JPA cache so findById fetches fresh data

            // then — only the ACTIVE alert was updated
            assertThat(updated).isEqualTo(1);
            assertThat(alertJpaRepository.findById(active.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.TRIGGERED_TODAY);
            assertThat(alertJpaRepository.findById(triggered.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.TRIGGERED_TODAY);
            assertThat(alertJpaRepository.findById(deleted.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.DELETED);
        }
    }

    @Nested
    class Layer3NotificationIdempotencyKey {

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

            // when — insert first
            insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("155.50"));
            // when — insert duplicate with same idempotency_key
            insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));

            // then — only 1 row (first insert wins, second is silently skipped)
            assertThat(notificationJpaRepository.count()).isEqualTo(1);
            var notification = notificationJpaRepository.findAll().getFirst();
            assertThat(notification.getTriggerPrice()).isEqualByComparingTo(new BigDecimal("155.50"));
        }

        @Test
        void shouldAllowNotificationsWithDifferentIdempotencyKeys() {
            // given — same alert, different trading dates
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var today = LocalDate.now();
            var yesterday = today.minusDays(1);

            // when — different idempotency keys (different trading dates)
            insertNotificationIdempotent(alert, alert.getId() + ":" + today, new BigDecimal("155.50"));
            insertNotificationIdempotent(alert, alert.getId() + ":" + yesterday, new BigDecimal("148.00"));

            // then — both notifications exist
            assertThat(notificationJpaRepository.count()).isEqualTo(2);
        }

        @Test
        void shouldAllowNotificationsForDifferentAlertsOnSameDay() {
            // given — different alerts, same day
            var alert1 = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var alert2 = createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var today = LocalDate.now();

            // when
            insertNotificationIdempotent(alert1, alert1.getId() + ":" + today, new BigDecimal("155.50"));
            insertNotificationIdempotent(alert2, alert2.getId() + ":" + today, new BigDecimal("210.00"));

            // then
            assertThat(notificationJpaRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    class Layer4TriggerLogDedup {

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

            // when — insert first
            insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("155.50"));
            // when — insert duplicate (same alert_id + trading_date)
            insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

            // then — only 1 row
            assertThat(triggerLogJpaRepository.count()).isEqualTo(1);
            var log = triggerLogJpaRepository.findAll().getFirst();
            assertThat(log.getTriggerPrice()).isEqualByComparingTo(new BigDecimal("155.50"));
        }

        @Test
        void shouldAllowTriggerLogsForSameAlertOnDifferentDays() {
            // given
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
            var today = LocalDate.now();
            var yesterday = today.minusDays(1);

            // when
            insertTriggerLogIdempotent(alert, today, new BigDecimal("155.50"));
            insertTriggerLogIdempotent(alert, yesterday, new BigDecimal("148.00"));

            // then — both logs exist
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

    @Nested
    class CrossLayerDedup {

        @Test
        void shouldHandleFullDuplicateTriggerGracefully() {
            // given — alert exists
            var alert = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            var tradingDate = LocalDate.now();
            var triggerPrice = new BigDecimal("155.50");
            var idempotencyKey = alert.getId() + ":" + tradingDate;

            // when — first trigger: update status + persist notification + persist trigger log
            jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                    alert.getId());
            insertNotificationIdempotent(alert, idempotencyKey, triggerPrice);
            insertTriggerLogIdempotent(alert, tradingDate, triggerPrice);

            // then — 1 of each
            assertThat(alertJpaRepository.findById(alert.getId()).orElseThrow().getStatus())
                    .isEqualTo(AlertStatus.TRIGGERED_TODAY);
            assertThat(notificationJpaRepository.count()).isEqualTo(1);
            assertThat(triggerLogJpaRepository.count()).isEqualTo(1);

            // when — duplicate trigger (all layers should be no-ops)
            var statusUpdate = jdbcTemplate.update(
                    "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
                    alert.getId());
            insertNotificationIdempotent(alert, idempotencyKey, new BigDecimal("156.00"));
            insertTriggerLogIdempotent(alert, tradingDate, new BigDecimal("156.00"));

            // then — still 1 of each, no duplicates
            assertThat(statusUpdate).isZero();
            assertThat(notificationJpaRepository.count()).isEqualTo(1);
            assertThat(triggerLogJpaRepository.count()).isEqualTo(1);

            // and — original values preserved (first writer wins)
            var notification = notificationJpaRepository.findAll().getFirst();
            assertThat(notification.getTriggerPrice()).isEqualByComparingTo(triggerPrice);
        }
    }

    // --- Helper methods ---

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

    private void insertNotificationIdempotent(AlertEntity alert, String idempotencyKey, BigDecimal triggerPrice) {
        var id = "notif_" + System.nanoTime();
        var triggerId = "trig_" + System.nanoTime();
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, alert_trigger_id, alert_id, user_id, symbol,
                    threshold_price, trigger_price, direction, note, idempotency_key, created_at, read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                id, triggerId, alert.getId(), alert.getUserId(), alert.getSymbol(),
                alert.getThresholdPrice(), triggerPrice, alert.getDirection().name(),
                alert.getNote(), idempotencyKey, Timestamp.from(Instant.now()), false
        );
    }

    private void insertTriggerLogIdempotent(AlertEntity alert, LocalDate tradingDate, BigDecimal triggerPrice) {
        var id = "log_" + System.nanoTime();
        var now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO alert_trigger_log (id, alert_id, user_id, symbol,
                    threshold_price, trigger_price, tick_timestamp, triggered_at, trading_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (alert_id, trading_date) DO NOTHING
                """,
                id, alert.getId(), alert.getUserId(), alert.getSymbol(),
                alert.getThresholdPrice(), triggerPrice, now, now, java.sql.Date.valueOf(tradingDate)
        );
    }
}
