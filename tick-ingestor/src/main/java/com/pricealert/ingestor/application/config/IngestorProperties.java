package com.pricealert.ingestor.application.config;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestor")
public record IngestorProperties(
        @NotBlank String simulatorUrl, List<String> subscribeSymbols, ReconnectConfig reconnect) {
    public record ReconnectConfig(long initialDelayMs, long maxDelayMs, int multiplier) {}
}
