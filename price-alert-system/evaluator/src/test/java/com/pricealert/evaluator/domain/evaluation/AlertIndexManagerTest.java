package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

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
    void getOrCreate_createsNewIndex() {
        var index = manager.getOrCreate("AAPL");
        assertThat(index).isNotNull();
        assertThat(index.isEmpty()).isTrue();
    }

    @Test
    void getOrCreate_returnsSameInstance() {
        var first = manager.getOrCreate("AAPL");
        var second = manager.getOrCreate("AAPL");
        assertThat(first).isSameAs(second);
    }

    @Test
    void get_nonExistent_returnsNull() {
        assertThat(manager.get("AAPL")).isNull();
    }

    @Test
    void addAlert_createsIndexAndAdds() {
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        assertThat(manager.totalAlerts()).isEqualTo(1);
        assertThat(manager.symbolCount()).isEqualTo(1);
    }

    @Test
    void addAlert_multipleSymbols() {
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        assertThat(manager.totalAlerts()).isEqualTo(2);
        assertThat(manager.symbolCount()).isEqualTo(2);
    }

    @Test
    void removeAlert_removesFromCorrectSymbol() {
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        manager.removeAlert("a1", "AAPL");

        assertThat(manager.totalAlerts()).isEqualTo(1);
        assertThat(manager.get("AAPL").isEmpty()).isTrue();
        assertThat(manager.get("MSFT").isEmpty()).isFalse();
    }

    @Test
    void removeAlert_nonExistentSymbol_noOp() {
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));

        manager.removeAlert("a1", "NONEXISTENT");

        assertThat(manager.totalAlerts()).isEqualTo(1);
    }

    @Test
    void clear_removesAllIndices() {
        manager.addAlert(alert("a1", "AAPL", new BigDecimal("150.00"), Direction.ABOVE));
        manager.addAlert(alert("a2", "MSFT", new BigDecimal("300.00"), Direction.ABOVE));

        manager.clear();

        assertThat(manager.totalAlerts()).isZero();
        assertThat(manager.symbolCount()).isZero();
    }
}
