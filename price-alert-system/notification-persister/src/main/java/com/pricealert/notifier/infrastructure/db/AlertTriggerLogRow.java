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
import java.time.LocalDate;

@Entity
@Table(name = "alert_trigger_log")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AlertTriggerLogRow {

    @Id
    @Column(length = 26)
    private String id;

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

    @Column(name = "tick_timestamp", nullable = false)
    private Instant tickTimestamp;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;
}
