package com.pricealert.alertapi.application.controller;

import com.pricealert.alertapi.domain.exceptions.AlertNotFoundException;
import com.pricealert.alertapi.domain.exceptions.AlertNotOwnedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AlertNotFoundException.class)
    public ProblemDetail handleAlertNotFound(AlertNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Alert Not Found");
        problem.setProperty("code", ErrorCodes.ALERT_NOT_FOUND);
        return problem;
    }

    @ExceptionHandler(AlertNotOwnedException.class)
    public ProblemDetail handleAlertNotOwned(AlertNotOwnedException ex) {
        log.warn("Ownership violation: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Alert not found");
        problem.setTitle("Alert Not Found");
        problem.setProperty("code", ErrorCodes.ALERT_NOT_FOUND);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setProperty("code", ErrorCodes.VALIDATION_ERROR);
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        problem.setTitle("Internal Server Error");
        problem.setProperty("code", ErrorCodes.INTERNAL_ERROR);
        return problem;
    }
}
