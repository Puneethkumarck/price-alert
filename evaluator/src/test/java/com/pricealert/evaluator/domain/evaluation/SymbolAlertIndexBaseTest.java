package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;

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
