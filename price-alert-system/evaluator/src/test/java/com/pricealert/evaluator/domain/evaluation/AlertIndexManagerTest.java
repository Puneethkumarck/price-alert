package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertIndexManagerTest {

    private AlertIndexManager manager;

    @BeforeEach
    void setUp() {
        manager = new AlertIndexManager();
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
    void shouldCreateNewIndexOnFirstGetOrCreate() {
        // when
        var index = manager.getOrCreate("AAPL");

        // then
        assertThat(index).isNotNull();
        assertThat(index.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnSameInstanceOnSubsequentGetOrCreate() {
        // when
        var first = manager.getOrCreate("AAPL");
        var second = manager.getOrCreate("AAPL");

        // then
        assertThat(first).isSameAs(second);
    }

    @Test
    void shouldReturnNullForNonExistentSymbol() {
        // when/then
        assertThat(manager.get("AAPL")).isNull();
    }

    @Test
    void shouldCreateIndexAndAddAlertOnAddAlert() {
        // when
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        // then
        assertThat(manager.totalAlerts()).isEqualTo(1);
        assertThat(manager.symbolCount()).isEqualTo(1);
    }

    @Test
    void shouldTrackMultipleSymbolsIndependently() {
        // when
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        // then
        assertThat(manager.totalAlerts()).isEqualTo(2);
        assertThat(manager.symbolCount()).isEqualTo(2);
    }

    @Test
    void shouldRemoveAlertFromCorrectSymbol() {
        // given
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        // when
        manager.removeAlert("a1", "AAPL");

        // then
        assertThat(manager.totalAlerts()).isEqualTo(1);
        assertThat(manager.get("AAPL").isEmpty()).isTrue();
        assertThat(manager.get("MSFT").isEmpty()).isFalse();
    }

    @Test
    void shouldBeNoOpWhenRemovingFromNonExistentSymbol() {
        // given
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        manager.removeAlert("a1", "NONEXISTENT");

        // then
        assertThat(manager.totalAlerts()).isEqualTo(1);
    }

    @Test
    void shouldRemoveAllIndicesOnClear() {
        // given
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        // when
        manager.clear();

        // then
        assertThat(manager.totalAlerts()).isZero();
        assertThat(manager.symbolCount()).isZero();
    }
}
