package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexBelowTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldFireWhenPriceBelowThreshold() {
        // given
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

        // when
        var fired = index.evaluate(new BigDecimal("145.00"));

        // then
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("b1");
    }

    @Test
    void shouldFireWhenPriceEqualsThreshold() {
        // given
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

        // when/then
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void shouldNotFireWhenPriceAboveThreshold() {
        // given
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

        // when/then
        assertThat(index.evaluate(new BigDecimal("150.01"))).isEmpty();
    }

    @Test
    void shouldBeRemovedFromIndexAfterFiring() {
        // given
        index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

        // when
        index.evaluate(new BigDecimal("145.00"));

        // then
        assertThat(index.size()).isZero();
    }
}
