package com.pricealert.alertapi.domain.exceptions;

public class RateLimitExceededException extends RuntimeException {

    private RateLimitExceededException(String message) {
        super(message);
    }

    public static RateLimitExceededException alertCreationLimit(int max) {
        return new RateLimitExceededException("Alert creation limit: " + max + " per minute");
    }
}
