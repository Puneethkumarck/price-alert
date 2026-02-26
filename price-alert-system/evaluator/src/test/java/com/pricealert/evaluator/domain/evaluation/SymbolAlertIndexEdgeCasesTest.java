package com.pricealert.evaluator.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SymbolAlertIndexEdgeCasesTest extends SymbolAlertIndexBaseTest {

    @Test
    void shouldReturnEmptyWhenIndexIsEmpty() {
        // when/then
        assertThat(index.evaluate(new BigDecimal("150.00"))).isEmpty();
    }

    @Test
    void shouldUpdateLastPriceAfterEvaluation() {
        // when
        index.evaluate(new BigDecimal("150.00"));

        // then
        assertThat(index.getLastPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldFireForVerySmallPrice() {
        // given
        index.addAlert(alert("a1", new BigDecimal("0.01"), Direction.ABOVE));

        // when/then
        assertThat(index.evaluate(new BigDecimal("0.01"))).hasSize(1);
    }

    @Test
    void shouldFireForVeryLargePrice() {
        // given
        index.addAlert(alert("a1", new BigDecimal("999999.99"), Direction.BELOW));

        // when/then
        assertThat(index.evaluate(new BigDecimal("999999.99"))).hasSize(1);
    }

    @Test
    void shouldOnlyFireOnceOnConsecutiveEvaluations() {
        // given
        index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

        // when
        var first = index.evaluate(new BigDecimal("155.00"));
        var second = index.evaluate(new BigDecimal("160.00"));

        // then
        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }
}
