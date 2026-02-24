package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
    void evaluate_noIndex_returnsEmpty() {
        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());
        assertThat(triggers).isEmpty();
    }

    @Test
    void evaluate_emptyIndex_returnsEmpty() {
        indexManager.getOrCreate("AAPL");

        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());
        assertThat(triggers).isEmpty();
    }

    @Test
    void evaluate_aboveAlert_producesCorrectTrigger() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        var triggers = engine.evaluate("AAPL", new BigDecimal("155.00"), Instant.now());

        assertThat(triggers).hasSize(1);
        var trigger = triggers.getFirst();
        assertThat(trigger.alertId()).isEqualTo("a1");
        assertThat(trigger.userId()).isEqualTo("user1");
        assertThat(trigger.symbol()).isEqualTo("AAPL");
        assertThat(trigger.thresholdPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(trigger.triggerPrice()).isEqualByComparingTo(new BigDecimal("155.00"));
        assertThat(trigger.direction()).isEqualTo(Direction.ABOVE);
        assertThat(trigger.triggerId()).isNotNull();
        assertThat(trigger.tickTimestamp()).isNotNull();
        assertThat(trigger.triggeredAt()).isNotNull();
        assertThat(trigger.tradingDate()).isNotNull();
    }

    @Test
    void evaluate_differentSymbols_independent() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        var aaplTriggers = engine.evaluate("AAPL", new BigDecimal("155.00"), Instant.now());
        var msftTriggers = engine.evaluate("MSFT", new BigDecimal("290.00"), Instant.now());

        assertThat(aaplTriggers).hasSize(1);
        assertThat(msftTriggers).isEmpty();
    }

    @Test
    void evaluate_multipleAlertsSameSymbol_allFire() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "AAPL", new BigDecimal("155.00"), Direction.ABOVE));

        var triggers = engine.evaluate("AAPL", new BigDecimal("160.00"), Instant.now());

        assertThat(triggers).hasSize(2);
        assertThat(triggers).extracting(t -> t.alertId())
                .containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void evaluate_firedAlertsNotRepeated() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        var first = engine.evaluate("AAPL", new BigDecimal("155.00"), Instant.now());
        var second = engine.evaluate("AAPL", new BigDecimal("160.00"), Instant.now());

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    @Test
    void evaluate_setsUniqueTriggerId() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("100.00"), Direction.ABOVE));
        indexManager.addAlert(alert("a2", "AAPL", new BigDecimal("100.00"), Direction.ABOVE));

        var triggers = engine.evaluate("AAPL", new BigDecimal("150.00"), Instant.now());

        assertThat(triggers).hasSize(2);
        assertThat(triggers.get(0).triggerId()).isNotEqualTo(triggers.get(1).triggerId());
    }

    @Test
    void evaluate_tradingDate_derivedFromTickTimestamp() {
        indexManager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        // 2026-02-23 14:30:00 UTC = 2026-02-23 09:30:00 ET
        var tickTimestamp = Instant.parse("2026-02-23T14:30:00Z");
        var triggers = engine.evaluate("AAPL", new BigDecimal("155.00"), tickTimestamp);

        assertThat(triggers.getFirst().tradingDate().toString()).isEqualTo("2026-02-23");
    }
}
