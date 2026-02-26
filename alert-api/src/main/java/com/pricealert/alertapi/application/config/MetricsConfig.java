package com.pricealert.alertapi.application.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter alertsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("alerts.created")
                .description("Total alerts created")
                .register(registry);
    }

    @Bean
    public Counter alertsDeletedCounter(MeterRegistry registry) {
        return Counter.builder("alerts.deleted")
                .description("Total alerts soft-deleted")
                .register(registry);
    }

    @Bean
    public Counter alertsUpdatedCounter(MeterRegistry registry) {
        return Counter.builder("alerts.updated")
                .description("Total alerts updated")
                .register(registry);
    }
}
