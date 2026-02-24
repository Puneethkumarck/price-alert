package com.pricealert.notifier.application.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter notificationsPersistedCounter(MeterRegistry registry) {
        return Counter.builder("notifications.persisted")
                .description("Total notifications successfully persisted")
                .register(registry);
    }

    @Bean
    public Counter notificationsDeduplicatedCounter(MeterRegistry registry) {
        return Counter.builder("notifications.deduplicated")
                .description("Total duplicate notifications skipped")
                .register(registry);
    }
}
