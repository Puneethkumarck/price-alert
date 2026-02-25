package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;

public abstract class SymbolAlertIndexBaseTest {

    SymbolAlertIndex index;

    @BeforeEach
    void setUp() {
        index = new SymbolAlertIndex();
    }

    AlertEntry alert(String id, BigDecimal threshold, Direction direction) {
        return AlertEntry.builder()
                .alertId(id)
                .userId("user1")
                .symbol("AAPL")
                .thresholdPrice(threshold)
                .direction(direction)
                .build();
    }
}
