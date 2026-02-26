package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexAddRemoveTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldIncrementSizeWhenAlertAdded() {
        // when
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // then
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.isEmpty()).isFalse();
    }

    @Test
    void shouldDecrementSizeWhenAlertRemoved() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        index.removeAlert("a1");

        // then
        assertThat(index.size()).isZero();
        assertThat(index.isEmpty()).isTrue();
    }

    @Test
    void shouldBeNoOpWhenRemovingNonExistentAlert() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        index.removeAlert("nonexistent");

        // then
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void shouldSupportMultipleAlertsAtSameThreshold() {
        // when
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("150.00"), Direction.ABOVE));

        // then
        assertThat(index.size()).isEqualTo(2);
    }
}
