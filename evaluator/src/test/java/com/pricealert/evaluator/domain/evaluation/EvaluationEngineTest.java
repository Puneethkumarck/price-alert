package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.AlertTrigger;
import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluationEngineTest {

    private AlertIndexManager indexManager;
    private EvaluationEngine engine;

    @BeforeEach
    void setUp() {
        indexManager = new AlertIndexManager();
        engine = new EvaluationEngine(indexManager);
    }

    private AlertEntry alert(String id, String symbol, BigDecimal threshold, Direction direction) {
        return AlertEntry.builder()
                .alertId(id)
                .userId("user1")
                .symbol(symbol)
                .thresholdPrice(threshold)
                .direction(direction)
                .build();
    }

    @Test
    void shouldReturnEmptyWhenNoIndexExistsForSymbol() {
        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());

        // then
        assertThat(triggers).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenIndexIsEmpty() {
        // given
        indexManager.getOrCreate("AAPL");

        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());

        // then
        assertThat(triggers).isEmpty();
    }

    @Test
    void shouldProduceCorrectTriggerForAboveAlert() {
        // given
        var tickTimestamp = Instant.now();
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("155.00"), tickTimestamp);

        // then
        assertThat(triggers).hasSize(1);
        var trigger = triggers.getFirst();
        var expectedTrigger =
                AlertTrigger.builder()
                        .triggerId(trigger.triggerId())
                        .alertId("a1")
                        .userId("user1")
                        .symbol("AAPL")
                        .thresholdPrice(new BigDecimal("150.00"))
                        .triggerPrice(new BigDecimal("155.00"))
                        .direction(Direction.ABOVE)
                        .tickTimestamp(trigger.tickTimestamp())
                        .triggeredAt(trigger.triggeredAt())
                        .tradingDate(trigger.tradingDate())
                        .build();
        assertThat(trigger)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expectedTrigger);
    }

    @Test
    void shouldEvaluateDifferentSymbolsIndependently() {
        // given
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        // when
        var aaplTriggers = engine.evaluate("AAPL", new BigDecimal("155.00"), Instant.now());
        var msftTriggers = engine.evaluate("MSFT", new BigDecimal("290.00"), Instant.now());

        // then
        assertThat(aaplTriggers).hasSize(1);
        assertThat(msftTriggers).isEmpty();
    }

    @Test
    void shouldFireAllMatchingAlertsForSameSymbol() {
        // given
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "AAPL", new BigDecimal("155.00"), Direction.ABOVE));

        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("160.00"), Instant.now());

        // then
        assertThat(triggers).hasSize(2);
        assertThat(triggers).extracting(t -> t.alertId()).containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void shouldNotRepeatFiredAlertsOnSubsequentEvaluations() {
        // given
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        var first = engine.evaluate("AAPL", new BigDecimal("155.00"), Instant.now());
        var second = engine.evaluate("AAPL", new BigDecimal("160.00"), Instant.now());

        // then
        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    void shouldAssignUniqueTriggerId() {
        // given
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("100.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "AAPL", new BigDecimal("100.00"), Direction.ABOVE));

        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());

        // then
        assertThat(triggers).hasSize(2);
        assertThat(triggers.get(0).triggerId()).isNotEqualTo(triggers.get(1).triggerId());
    }

    @Test
    void shouldDeriveTradingDateFromTickTimestamp() {
        // given
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        // 2026-02-23 14:30:00 UTC = 2026-02-23 09:30:00 ET
        var tickTimestamp = Instant.parse("2026-02-23T14:30:00Z");

        // when
        var triggers = engine.evaluate("AAPL", new BigDecimal("155.00"), tickTimestamp);

        // then
        assertThat(triggers.getFirst().tradingDate().toString()).isEqualTo("2026-02-23");
    }
}
