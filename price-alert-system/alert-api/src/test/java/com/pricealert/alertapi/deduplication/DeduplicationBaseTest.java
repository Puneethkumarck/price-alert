package com.pricealert.alertapi.deduplication;

import com.pricealert.alertapi.BaseIntegrationTest;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationJpaRepository;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import com.pricealert.common.id.UlidGenerator;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DeduplicationBaseTest extends BaseIntegrationTest {

    static final String USER_ID = "user_dedup_001";

    @Autowired AlertJpaRepository alertJpaRepository;

    @Autowired NotificationJpaRepository notificationJpaRepository;

    @Autowired AlertTriggerLogJpaRepository triggerLogJpaRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired EntityManager entityManager;

    AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        return alertJpaRepository.save(
                AlertEntity.builder()
                        .id(UlidGenerator.generate())
                        .userId(userId)
                        .symbol(symbol)
                        .thresholdPrice(new BigDecimal("150.00"))
                        .direction(Direction.ABOVE)
                        .status(status)
                        .note("Test alert")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
    }

    void insertNotificationIdempotent(
            AlertEntity alert, String idempotencyKey, BigDecimal triggerPrice) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (id, alert_trigger_id, alert_id, user_id, symbol,
                    threshold_price, trigger_price, direction, note, idempotency_key, created_at, read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                UlidGenerator.generate(),
                UlidGenerator.generate(),
                alert.getId(),
                alert.getUserId(),
                alert.getSymbol(),
                alert.getThresholdPrice(),
                triggerPrice,
                alert.getDirection().name(),
                alert.getNote(),
                idempotencyKey,
                Timestamp.from(Instant.now()),
                false);
    }

    void insertTriggerLogIdempotent(
            AlertEntity alert, LocalDate tradingDate, BigDecimal triggerPrice) {
        var now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO alert_trigger_log (id, alert_id, user_id, symbol,
                    threshold_price, trigger_price, tick_timestamp, triggered_at, trading_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (alert_id, trading_date) DO NOTHING
                """,
                UlidGenerator.generate(),
                alert.getId(),
                alert.getUserId(),
                alert.getSymbol(),
                alert.getThresholdPrice(),
                triggerPrice,
                now,
                now,
                java.sql.Date.valueOf(tradingDate));
    }
}
