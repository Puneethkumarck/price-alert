package com.pricealert.alertapi.domain.exceptions;

public class AlertNotFoundException extends RuntimeException {

    private AlertNotFoundException(String message) {
        super(message);
    }

    public static AlertNotFoundException of(String alertId) {
        return new AlertNotFoundException("Alert not found: " + alertId);
    }
}
