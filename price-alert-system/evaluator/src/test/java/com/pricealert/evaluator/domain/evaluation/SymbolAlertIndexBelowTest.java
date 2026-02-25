package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexBelowTest extends SymbolAlertIndexBaseTest {

    @Test
    void fires_whenPriceBelowThreshold() {
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));
        var fired = index.evaluate(new BigDecimal("145.00"));
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("b1");
    }

    @Test
    void fires_whenPriceEqualsThreshold() {
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void doesNotFire_whenPriceAboveThreshold() {
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));
        assertThat(index.evaluate(new BigDecimal("150.01"))).isEmpty();
    }

    @Test
    void removedFromIndexAfterFiring() {
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));
        index.evaluate(new BigDecimal("145.00"));
        assertThat(index.size()).isZero();
    }
}
