package com.pricealert.alertapi.application.controller.alert;

import com.pricealert.common.event.Direction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record UpdateAlertRequest(
        @DecimalMin(value = "0.000001", message = "Threshold must be positive")
                @Digits(integer = 6, fraction = 6, message = "Max 6 integer + 6 decimal digits")
                BigDecimal thresholdPrice,
        Direction direction,
        String note) {}
