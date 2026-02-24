package com.pricealert.evaluator.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "evaluator")
public record EvaluatorProperties(Warmup warmup) {

    public record Warmup(int batchSize) {
    }
}
