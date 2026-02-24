package com.pricealert.alertapi.domain.alert;

import com.pricealert.alertapi.domain.exceptions.AlertNotFoundException;
import com.pricealert.alertapi.domain.exceptions.AlertNotOwnedException;
import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import com.pricealert.common.id.UlidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertEventPublisher eventPublisher;

public Alert createAlert(String userId, String symbol, BigDecimal thresholdPrice, Direction direction, String note) {
        var now = Instant.now();
        var alert = Alert.builder()
                .id(UlidGenerator.generate())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(thresholdPrice)
                .direction(direction)
                .status(AlertStatus.ACTIVE)
                .note(note)
                .createdAt(now)
                .updatedAt(now)
                .build();

        var saved = alertRepository.save(alert);
        eventPublisher.publish(toAlertChange(saved, AlertChangeType.CREATED));
        log.info("Created alert {} for user {} on {}", saved.id(), userId, symbol);
        return saved;
    }

public Alert getAlert(String alertId, String userId) {
        var alert = alertRepository.findById(alertId)
                .orElseThrow(() -> AlertNotFoundException.of(alertId));
        if (!alert.userId().equals(userId)) {
            throw AlertNotOwnedException.of(alertId, userId);
        }
        return alert;
    }

public Page<Alert> listAlerts(String userId, AlertStatus status, String symbol, Pageable pageable) {
        return alertRepository.findByUserIdAndOptionalFilters(userId, status, symbol, pageable);
    }

public Alert updateAlert(String alertId, String userId, BigDecimal thresholdPrice, Direction direction, String note) {
        var alert = getAlert(alertId, userId);

        var updated = alert.toBuilder()
                .thresholdPrice(thresholdPrice != null ? thresholdPrice : alert.thresholdPrice())
                .direction(direction != null ? direction : alert.direction())
                .note(note != null ? note : alert.note())
                .updatedAt(Instant.now())
                .build();

        var saved = alertRepository.save(updated);
        eventPublisher.publish(toAlertChange(saved, AlertChangeType.UPDATED));
        log.info("Updated alert {}", alertId);
        return saved;
    }

public void deleteAlert(String alertId, String userId) {
        var alert = getAlert(alertId, userId);

        var deleted = alert.toBuilder()
                .status(AlertStatus.DELETED)
                .updatedAt(Instant.now())
                .build();

        alertRepository.save(deleted);
        eventPublisher.publish(toAlertChange(deleted, AlertChangeType.DELETED));
        log.info("Soft-deleted alert {}", alertId);
    }

    private AlertChange toAlertChange(Alert alert, AlertChangeType type) {
        return AlertChange.builder()
                .eventType(type)
                .alertId(alert.id())
                .userId(alert.userId())
                .symbol(alert.symbol())
                .thresholdPrice(alert.thresholdPrice())
                .direction(alert.direction())
                .timestamp(Instant.now())
                .build();
    }
}
