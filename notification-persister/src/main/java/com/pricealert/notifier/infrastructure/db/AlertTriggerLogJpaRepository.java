package com.pricealert.notifier.infrastructure.db;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AlertTriggerLogJpaRepository extends JpaRepository<AlertTriggerLogRow, String> {

    @Modifying
    @Query(
            value =
                    "INSERT INTO alert_trigger_log (id, alert_id, user_id, symbol, threshold_price,"
                        + " trigger_price, tick_timestamp, triggered_at, trading_date) VALUES"
                        + " (:#{#row.id}, :#{#row.alertId}, :#{#row.userId}, :#{#row.symbol},"
                        + " :#{#row.thresholdPrice}, :#{#row.triggerPrice}, :#{#row.tickTimestamp},"
                        + " :#{#row.triggeredAt}, :#{#row.tradingDate}) ON CONFLICT (alert_id,"
                        + " trading_date) DO NOTHING",
            nativeQuery = true)
    void insertIdempotent(AlertTriggerLogRow row);

    boolean existsByAlertIdAndTradingDate(String alertId, LocalDate tradingDate);
}
