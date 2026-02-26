package com.pricealert.evaluator.infrastructure.db;

import com.pricealert.common.event.Direction;
import com.pricealert.evaluator.application.config.EvaluatorProperties;
import com.pricealert.evaluator.domain.evaluation.AlertEntry;
import com.pricealert.evaluator.domain.evaluation.AlertIndexManager;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmUpService {

    private static final String WARM_UP_SQL =
            "SELECT id, user_id, symbol, threshold_price, direction, note "
                    + "FROM alerts WHERE status = 'ACTIVE'";

    private final JdbcTemplate jdbcTemplate;
    private final AlertIndexManager indexManager;
    private final EvaluatorProperties properties;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info(
                "Starting evaluator warm-up: streaming ACTIVE alerts from DB (fetch-size={})",
                properties.warmup().batchSize());

        // Kafka listener containers are configured with autoStartup=false so that
        // consumer threads cannot call addAlert() on SymbolAlertIndex.aboveAlerts /
        // belowAlerts (plain TreeMap, not thread-safe) while this warm-up loop is
        // also calling computeIfAbsent() on the same maps from the main thread.
        // Starting them only after the index is fully built eliminates the
        // ConcurrentModificationException observed at 800K+ alerts.
        AtomicLong count = new AtomicLong();
        jdbcTemplate.setFetchSize(properties.warmup().batchSize());
        jdbcTemplate.query(
                WARM_UP_SQL,
                rs -> {
                    indexManager.addAlert(
                            AlertEntry.builder()
                                    .alertId(rs.getString("id"))
                                    .userId(rs.getString("user_id"))
                                    .symbol(rs.getString("symbol"))
                                    .thresholdPrice(
                                            rs.getObject("threshold_price", BigDecimal.class))
                                    .direction(Direction.valueOf(rs.getString("direction")))
                                    .note(rs.getString("note"))
                                    .build());
                    count.incrementAndGet();
                });

        log.info(
                "Evaluator warm-up complete: loaded {} alerts across {} symbols",
                count.get(),
                indexManager.symbolCount());

        // Index is fully built — now safe to start Kafka consumers.
        log.info("Starting Kafka listener containers");
        kafkaListenerEndpointRegistry.start();
        log.info("Kafka listener containers started");
    }
}
