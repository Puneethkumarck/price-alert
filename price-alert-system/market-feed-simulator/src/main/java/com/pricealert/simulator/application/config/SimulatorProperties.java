package com.pricealert.simulator.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        String mode,
        long tickIntervalMs,
        String symbolsFile,
        double volatility,
        MarketHours marketHours
) {

    public record MarketHours(
            String open,
            String close,
            String timezone,
            boolean respectHours
    ) {
    }
}
