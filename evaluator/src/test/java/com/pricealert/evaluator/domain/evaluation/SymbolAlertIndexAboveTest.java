package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexAboveTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldFireWhenPriceAboveThreshold() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        var fired = index.evaluate(new BigDecimal("155.00"));

        // then
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("a1");
    }

    @Test
    void shouldFireWhenPriceEqualsThreshold() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when/then
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void shouldNotFireWhenPriceBelowThreshold() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when/then
        assertThat(index.evaluate(new BigDecimal("149.99"))).isEmpty();
    }

    @Test
    void shouldBeRemovedFromIndexAfterFiring() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        index.evaluate(new BigDecimal("155.00"));

        // then
        assertThat(index.size()).isZero();
        assertThat(index.evaluate(new BigDecimal("160.00"))).isEmpty();
    }

    @Test
    void shouldFireMultipleAlertsAtDifferentThresholds() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("155.00"), Direction.ABOVE));

        // when
        var fired = index.evaluate(new BigDecimal("160.00"));

        // then
        assertThat(fired).hasSize(2);
        assertThat(fired).extracting(AlertEntry::alertId).containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void shouldOnlyFireAlertsWhoseThresholdIsBelowPrice() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("200.00"), Direction.ABOVE));

        // when
        var fired = index.evaluate(new BigDecimal("175.00"));

        // then
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("a1");
        assertThat(index.size()).isEqualTo(1);
    }
}
