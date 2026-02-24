package com.pricealert.evaluator.application.config;

import com.pricealert.evaluator.domain.evaluation.AlertIndexManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter ticksProcessedCounter(MeterRegistry registry) {
        return Counter.builder("evaluator.ticks.processed")
                .description("Total market ticks evaluated")
                .register(registry);
    }

    @Bean
    public Counter alertsTriggeredCounter(MeterRegistry registry) {
        return Counter.builder("evaluator.alerts.triggered")
                .description("Total alerts triggered")
                .register(registry);
    }

    @Bean
    public Gauge indexedSymbolsGauge(MeterRegistry registry, AlertIndexManager indexManager) {
        return Gauge.builder("evaluator.index.symbols", indexManager::symbolCount)
                .description("Number of symbols in the evaluation index")
                .register(registry);
    }

    @Bean
    public Gauge indexedAlertsGauge(MeterRegistry registry, AlertIndexManager indexManager) {
        return Gauge.builder("evaluator.index.alerts", indexManager::totalAlerts)
                .description("Total alerts in the evaluation index")
                .register(registry);
    }
}
