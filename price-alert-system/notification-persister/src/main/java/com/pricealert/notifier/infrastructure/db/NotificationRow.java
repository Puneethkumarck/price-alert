package com.pricealert.notifier.infrastructure.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRow {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "alert_trigger_id", nullable = false, length = 26)
    private String alertTriggerId;

    @Column(name = "alert_id", nullable = false, length = 26)
    private String alertId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "threshold_price", nullable = false, precision = 12, scale = 6)
    private BigDecimal thresholdPrice;

    @Column(name = "trigger_price", nullable = false, precision = 12, scale = 6)
    private BigDecimal triggerPrice;

    @Column(nullable = false, length = 5)
    private String direction;

    @Column(length = 255)
    private String note;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean read;
}
