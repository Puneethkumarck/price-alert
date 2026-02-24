package com.pricealert.alertapi.application.controller.alert;

import com.pricealert.alertapi.application.controller.alert.mapper.AlertRequestResponseMapper;
import com.pricealert.alertapi.domain.alert.AlertService;
import com.pricealert.common.event.AlertStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertRequestResponseMapper mapper;

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(@Valid @RequestBody CreateAlertRequest request, Authentication auth) {
        var userId = auth.getName();
        var alert = alertService.createAlert(
                userId,
                request.symbol(),
                request.thresholdPrice(),
                request.direction(),
                request.note()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(alert));
    }

    @GetMapping
    public Page<AlertResponse> listAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) String symbol,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        var userId = auth.getName();
        return alertService.listAlerts(userId, status, symbol, pageable)
                .map(mapper::toResponse);
    }

    @GetMapping("/{alertId}")
    public AlertResponse getAlert(@PathVariable String alertId, Authentication auth) {
        var userId = auth.getName();
        return mapper.toResponse(alertService.getAlert(alertId, userId));
    }

    @PatchMapping("/{alertId}")
    public AlertResponse updateAlert(
            @PathVariable String alertId,
            @Valid @RequestBody UpdateAlertRequest request,
            Authentication auth) {
        var userId = auth.getName();
        return mapper.toResponse(alertService.updateAlert(
                alertId, userId, request.thresholdPrice(), request.direction(), request.note()
        ));
    }

    @DeleteMapping("/{alertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@PathVariable String alertId, Authentication auth) {
        var userId = auth.getName();
        alertService.deleteAlert(alertId, userId);
    }
}
