package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexMixedDirectionsTest extends SymbolAlertIndexBaseTest {

    @Test
    void aboveAndBelowFireTogether() {
        index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
        index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));
        var fired = index.evaluate(new BigDecimal("150.00"));
        assertThat(fired).hasSize(2);
        assertThat(fired).extracting(AlertEntry::alertId).containsExactlyInAnyOrder("above1", "below1");
    }

    @Test
    void allThreeDirectionsFireTogether() {
        index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
        index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));
        index.addAlert(alert("cross1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));
        assertThat(index.evaluate(new BigDecimal("160.00"))).hasSize(3);
    }
}
