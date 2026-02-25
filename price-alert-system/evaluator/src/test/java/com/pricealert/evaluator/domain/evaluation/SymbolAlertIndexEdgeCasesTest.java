package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexEdgeCasesTest extends SymbolAlertIndexBaseTest {

    @Test
    void evaluate_emptyIndex_returnsEmpty() {
        assertThat(index.evaluate(new BigDecimal("150.00"))).isEmpty();
    }

    @Test
    void evaluate_updatesLastPrice() {
        index.evaluate(new BigDecimal("150.00"));
        assertThat(index.getLastPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void verySmallPrice() {
        index.addAlert(alert("a1", new BigDecimal("0.01"), Direction.ABOVE));
        assertThat(index.evaluate(new BigDecimal("0.01"))).hasSize(1);
    }

    @Test
    void veryLargePrice() {
        index.addAlert(alert("a1", new BigDecimal("999999.99"), Direction.BELOW));
        assertThat(index.evaluate(new BigDecimal("999999.99"))).hasSize(1);
    }

    @Test
    void consecutiveEvaluations_onlyFireOnce() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        var first = index.evaluate(new BigDecimal("155.00"));
        var second = index.evaluate(new BigDecimal("160.00"));
        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }
}
