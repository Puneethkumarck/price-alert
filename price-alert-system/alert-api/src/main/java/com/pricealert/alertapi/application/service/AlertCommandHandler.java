package com.pricealert.alertapi.application.service;

import com.pricealert.alertapi.domain.alert.Alert;
import com.pricealert.alertapi.domain.alert.AlertService;
import com.pricealert.alertapi.domain.exceptions.RateLimitExceededException;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import io.micrometer.core.instrument.Counter;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AlertCommandHandler {

    private static final int RATE_LIMIT_MAX = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final AlertService alertService;
    private final StringRedisTemplate redisTemplate;
    private final Counter alertsCreatedCounter;
    private final Counter alertsUpdatedCounter;
    private final Counter alertsDeletedCounter;

    @Transactional
    public Alert createAlert(
            String userId,
            String symbol,
            BigDecimal thresholdPrice,
            Direction direction,
            String note) {
        checkRateLimit(userId);
        var alert = alertService.createAlert(userId, symbol, thresholdPrice, direction, note);
        alertsCreatedCounter.increment();
        return alert;
    }

    private void checkRateLimit(String userId) {
        var key = "rate:alerts:" + userId;
        var count = redisTemplate.opsForValue().increment(key);
        if (count == 1L) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW);
        }
        if (count > RATE_LIMIT_MAX) {
            throw RateLimitExceededException.alertCreationLimit(RATE_LIMIT_MAX);
        }
    }

    @Transactional(readOnly = true)
    public Alert getAlert(String alertId, String userId) {
        return alertService.getAlert(alertId, userId);
    }

    @Transactional(readOnly = true)
    public Page<Alert> listAlerts(
            String userId, AlertStatus status, String symbol, Pageable pageable) {
        return alertService.listAlerts(userId, status, symbol, pageable);
    }

    @Transactional
    public Alert updateAlert(
            String alertId,
            String userId,
            BigDecimal thresholdPrice,
            Direction direction,
            String note) {
        var alert = alertService.updateAlert(alertId, userId, thresholdPrice, direction, note);
        alertsUpdatedCounter.increment();
        return alert;
    }

    @Transactional
    public void deleteAlert(String alertId, String userId) {
        alertService.deleteAlert(alertId, userId);
        alertsDeletedCounter.increment();
    }
}
