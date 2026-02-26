package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexCrossTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldFireWhenPriceCrossesThresholdUpward() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when
        var fired = index.evaluate(new BigDecimal("160.00"));

        // then
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("c1");
    }

    @Test
    void shouldFireWhenPriceCrossesThresholdDownward() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("160.00"));

        // when/then
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void shouldNotFireWhenPriceDoesNotCrossThreshold() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when/then
        assertThat(index.evaluate(new BigDecimal("153.00"))).isEmpty();
    }

    @Test
    void shouldNotFireWhenLastPriceIsNull() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));

        // when/then
        assertThat(index.evaluate(new BigDecimal("160.00"))).isEmpty();
    }

    @Test
    void shouldNotFireWhenPriceEqualsThresholdWithoutCrossing() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when/then
        assertThat(index.evaluate(new BigDecimal("155.00"))).isEmpty();
    }

    @Test
    void shouldNotFireWhenPriceIsUnchanged() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when/then
        assertThat(index.evaluate(new BigDecimal("150.00"))).isEmpty();
    }

    @Test
    void shouldBeRemovedFromIndexAfterFiring() {
        // given
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when
        index.evaluate(new BigDecimal("160.00"));

        // then
        assertThat(index.size()).isZero();
    }
}
