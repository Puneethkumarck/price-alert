package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexAddRemoveTest extends SymbolAlertIndexBaseTest {

    @Test
    void addAlert_incrementsSize() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.isEmpty()).isFalse();
    }

    @Test
    void removeAlert_decrementsSize() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.removeAlert("a1");
        assertThat(index.size()).isZero();
        assertThat(index.isEmpty()).isTrue();
    }

    @Test
    void removeAlert_nonExistent_noOp() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.removeAlert("nonexistent");
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void multipleAlertsAtSameThreshold() {
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));
        index.addAlert(alert("a2", new BigDecimal("150.00"), Direction.ABOVE));
        assertThat(index.size()).isEqualTo(2);
    }
}
