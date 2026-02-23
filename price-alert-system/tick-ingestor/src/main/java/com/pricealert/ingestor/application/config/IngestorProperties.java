package com.pricealert.ingestor.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ingestor")
public record IngestorProperties(
        String simulatorUrl,
        List<String> subscribeSymbols,
        ReconnectConfig reconnect
) {
    public record ReconnectConfig(
            long initialDelayMs,
            long maxDelayMs,
            int multiplier
    ) {}
}
