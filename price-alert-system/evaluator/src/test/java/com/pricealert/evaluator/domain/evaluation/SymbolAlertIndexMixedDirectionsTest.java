package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexMixedDirectionsTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldFireAboveAndBelowAlertsSimultaneously() {
        // given
        index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
        index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));

        // when
        var fired = index.evaluate(new BigDecimal("150.00"));

        // then
        assertThat(fired).hasSize(2);
        assertThat(fired)
                .extracting(AlertEntry::alertId)
                .containsExactlyInAnyOrder("above1", "below1");
    }

    @Test
    void shouldFireAllThreeDirectionsSimultaneously() {
        // given
        index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
        index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));
        index.addAlert(alert("cross1", new BigDecimal("155.00"), Direction.CROSS));
        index.setLastPrice(new BigDecimal("150.00"));

        // when/then
        assertThat(index.evaluate(new BigDecimal("160.00"))).hasSize(3);
    }
}
