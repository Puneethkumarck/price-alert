package com.pricealert.evaluator.infrastructure.kafka;

import com.pricealert.common.event.MarketTick;
import com.pricealert.common.kafka.KafkaTopics;
import com.pricealert.evaluator.domain.evaluation.EvaluationEngine;
import com.pricealert.evaluator.infrastructure.db.AlertStatusUpdater;
import io.micrometer.core.instrument.Counter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTickConsumer {

    private final EvaluationEngine evaluationEngine;
    private final AlertTriggerProducer triggerProducer;
    private final AlertStatusUpdater statusUpdater;
    private final Counter ticksProcessedCounter;
    private final Counter alertsTriggeredCounter;

    @KafkaListener(
            topics = KafkaTopics.MARKET_TICKS,
            groupId = "evaluator-ticks",
            containerFactory = "marketTickListenerContainerFactory")
    @Transactional
    public void onMarketTicks(List<MarketTick> ticks) {
        for (var tick : ticks) {
            var triggers = evaluationEngine.evaluate(tick.symbol(), tick.price(), tick.timestamp());
            ticksProcessedCounter.increment();

            for (var trigger : triggers) {
                log.info(
                        "Alert {} fired for {} at {} (threshold: {}, direction: {})",
                        trigger.alertId(),
                        trigger.symbol(),
                        trigger.triggerPrice(),
                        trigger.thresholdPrice(),
                        trigger.direction());

                triggerProducer.send(trigger);
                statusUpdater.markTriggeredToday(trigger.alertId());
                alertsTriggeredCounter.increment();
            }
        }
    }
}
