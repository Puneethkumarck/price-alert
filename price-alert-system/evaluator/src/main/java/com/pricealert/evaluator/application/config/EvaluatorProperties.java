package com.pricealert.evaluator.application.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "evaluator")
public record EvaluatorProperties(@NotNull @Valid Warmup warmup) {

    public record Warmup(@Min(1) int batchSize) {}
}
