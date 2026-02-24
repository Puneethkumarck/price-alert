package com.pricealert.notifier.infrastructure.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationJpaRepository extends JpaRepository<NotificationRow, String> {

    @Modifying
    @Query(value = "INSERT INTO notifications (id, alert_trigger_id, alert_id, user_id, symbol, threshold_price, trigger_price, direction, note, idempotency_key, created_at, read) " +
            "VALUES (:#{#row.id}, :#{#row.alertTriggerId}, :#{#row.alertId}, :#{#row.userId}, :#{#row.symbol}, :#{#row.thresholdPrice}, :#{#row.triggerPrice}, :#{#row.direction}, :#{#row.note}, :#{#row.idempotencyKey}, :#{#row.createdAt}, :#{#row.read}) " +
            "ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    void insertIdempotent(NotificationRow row);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
