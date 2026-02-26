package com.pricealert.simulator.application.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        @NotBlank String mode,
        @Min(1) long tickIntervalMs,
        @NotBlank String symbolsFile,
        double volatility,
        MarketHours marketHours) {

    public record MarketHours(String open, String close, String timezone, boolean respectHours) {}
}
