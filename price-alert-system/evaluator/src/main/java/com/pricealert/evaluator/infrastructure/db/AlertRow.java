package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the alerts table, used by the evaluator for warm-up and status updates.
 * All columns mapped to satisfy hibernate.ddl-auto=validate.
 */
@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AlertRow {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "threshold_price", nullable = false, precision = 12, scale = 6)
    private BigDecimal thresholdPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AlertStatus status;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_trigger_price", precision = 12, scale = 6)
    private BigDecimal lastTriggerPrice;
}
