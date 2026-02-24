package com.pricealert.alertapi.application.controller.alert;

import com.pricealert.common.event.Direction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAlertRequest(
        @NotNull @Pattern(regexp = "^[A-Z]{1,5}$", message = "Symbol must be 1-5 uppercase letters")
        String symbol,

        @NotNull @DecimalMin(value = "0.000001", message = "Threshold must be positive")
        @Digits(integer = 6, fraction = 6, message = "Max 6 integer + 6 decimal digits")
        BigDecimal thresholdPrice,

        @NotNull
        Direction direction,

        String note
) {
}
