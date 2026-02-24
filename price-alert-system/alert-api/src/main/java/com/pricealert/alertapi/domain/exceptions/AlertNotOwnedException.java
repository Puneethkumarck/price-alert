package com.pricealert.alertapi.domain.exceptions;

public class AlertNotOwnedException extends RuntimeException {

    private AlertNotOwnedException(String message) {
        super(message);
    }

    public static AlertNotOwnedException of(String alertId, String userId) {
        return new AlertNotOwnedException("Alert " + alertId + " is not owned by user " + userId);
    }
}
