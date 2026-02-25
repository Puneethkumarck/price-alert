package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexAboveTest extends SymbolAlertIndexBaseTest {

    @Test
    void fires_whenPriceAboveThreshold() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        var fired = index.evaluate(new BigDecimal("155.00"));
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("a1");
    }

    @Test
    void fires_whenPriceEqualsThreshold() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void doesNotFire_whenPriceBelowThreshold() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        assertThat(index.evaluate(new BigDecimal("149.99"))).isEmpty();
    }

    @Test
    void removedFromIndexAfterFiring() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.evaluate(new BigDecimal("155.00"));
        assertThat(index.size()).isZero();
        assertThat(index.evaluate(new BigDecimal("160.00"))).isEmpty();
    }

    @Test
    void multipleFire_atDifferentThresholds() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("155.00"), Direction.ABOVE));
        var fired = index.evaluate(new BigDecimal("160.00"));
        assertThat(fired).hasSize(2);
        assertThat(fired).extracting(AlertEntry::alertId).containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void partialFire_onlyThresholdsBelowPrice() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("200.00"), Direction.ABOVE));
        var fired = index.evaluate(new BigDecimal("175.00"));
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("a1");
        assertThat(index.size()).isEqualTo(1);
    }
}
