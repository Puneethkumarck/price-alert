package com.pricealert.alertapi.infrastructure.db.notification;

import com.pricealert.common.event.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Direction direction;

    @Column(length = 255)
    private String note;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean read;
}
