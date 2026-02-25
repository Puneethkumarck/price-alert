package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexCrossTest extends SymbolAlertIndexBaseTest {

    @Test
    void fires_whenPriceCrossesThresholdUpward() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        var fired = index.evaluate(new BigDecimal("160.00"));
        assertThat(fired).hasSize(1);
        assertThat(fired.getFirst().alertId()).isEqualTo("c1");
    }

    @Test
    void fires_whenPriceCrossesThresholdDownward() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("160.00"));
        assertThat(index.evaluate(new BigDecimal("150.00"))).hasSize(1);
    }

    @Test
    void doesNotFire_whenPriceDoesNotCross() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        assertThat(index.evaluate(new BigDecimal("153.00"))).isEmpty();
    }

    @Test
    void doesNotFire_whenLastPriceIsNull() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        assertThat(index.evaluate(new BigDecimal("160.00"))).isEmpty();
    }

    @Test
    void doesNotFire_whenPriceEqualsThreshold_noCross() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        assertThat(index.evaluate(new BigDecimal("155.00"))).isEmpty();
    }

    @Test
    void doesNotFire_whenPriceUnchanged() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        assertThat(index.evaluate(new BigDecimal("150.00"))).isEmpty();
    }

    @Test
    void removedFromIndexAfterFiring() {
        index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        index.evaluate(new BigDecimal("160.00"));
        assertThat(index.size()).isZero();
    }
}
