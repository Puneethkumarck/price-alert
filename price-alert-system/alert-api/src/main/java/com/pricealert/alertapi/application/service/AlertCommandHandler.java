package com.pricealert.alertapi.application.service;

import com.pricealert.alertapi.domain.alert.Alert;
import com.pricealert.alertapi.domain.alert.AlertService;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AlertCommandHandler {

    private final AlertService alertService;

    @Transactional
    public Alert createAlert(String userId, String symbol, BigDecimal thresholdPrice, Direction direction, String note) {
        return alertService.createAlert(userId, symbol, thresholdPrice, direction, note);
    }

    @Transactional(readOnly = true)
    public Alert getAlert(String alertId, String userId) {
        return alertService.getAlert(alertId, userId);
    }

    @Transactional(readOnly = true)
    public Page<Alert> listAlerts(String userId, AlertStatus status, String symbol, Pageable pageable) {
        return alertService.listAlerts(userId, status, symbol, pageable);
    }

    @Transactional
    public Alert updateAlert(String alertId, String userId, BigDecimal thresholdPrice, Direction direction, String note) {
        return alertService.updateAlert(alertId, userId, thresholdPrice, direction, note);
    }

    @Transactional
    public void deleteAlert(String alertId, String userId) {
        alertService.deleteAlert(alertId, userId);
    }
}
