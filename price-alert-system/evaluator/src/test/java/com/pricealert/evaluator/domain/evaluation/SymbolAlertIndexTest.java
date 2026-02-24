package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolAlertIndexTest {

    private SymbolAlertIndex index;

    @BeforeEach
    void setUp() {
        index = new SymbolAlertIndex();
    }

    private AlertEntry alert(String id, BigDecimal threshold, Direction direction) {
        return AlertEntry.builder()
                .alertId(id)
                .userId("user1")
                .symbol("AAPL")
                .thresholdPrice(threshold)
                .direction(direction)
                .build();
    }

    @Nested
    class AddAndRemove {

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

    @Nested
    class AboveAlerts {

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

            var fired = index.evaluate(new BigDecimal("150.00"));

            assertThat(fired).hasSize(1);
        }

        @Test
        void doesNotFire_whenPriceBelowThreshold() {
            index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

            var fired = index.evaluate(new BigDecimal("149.99"));

            assertThat(fired).isEmpty();
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

    @Nested
    class BelowAlerts {

        @Test
        void fires_whenPriceBelowThreshold() {
            index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

            var fired = index.evaluate(new BigDecimal("145.00"));

            assertThat(fired).hasSize(1);
            assertThat(fired.getFirst().alertId()).isEqualTo("b1");
        }

        @Test
        void fires_whenPriceEqualsThreshold() {
            index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

            var fired = index.evaluate(new BigDecimal("150.00"));

            assertThat(fired).hasSize(1);
        }

        @Test
        void doesNotFire_whenPriceAboveThreshold() {
            index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

            var fired = index.evaluate(new BigDecimal("150.01"));

            assertThat(fired).isEmpty();
        }

        @Test
        void removedFromIndexAfterFiring() {
            index.addAlert(alert("b1", new BigDecimal("150.00"), Direction.BELOW));

            index.evaluate(new BigDecimal("145.00"));

            assertThat(index.size()).isZero();
        }
    }

    @Nested
    class CrossAlerts {

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

            var fired = index.evaluate(new BigDecimal("150.00"));

            assertThat(fired).hasSize(1);
        }

        @Test
        void doesNotFire_whenPriceDoesNotCross() {
            index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
            index.setLastPrice(new BigDecimal("150.00"));

            var fired = index.evaluate(new BigDecimal("153.00"));

            assertThat(fired).isEmpty();
        }

        @Test
        void doesNotFire_whenLastPriceIsNull() {
            index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));

            var fired = index.evaluate(new BigDecimal("160.00"));

            assertThat(fired).isEmpty();
        }

        @Test
        void doesNotFire_whenPriceEqualsThreshold_noCross() {
            index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
            index.setLastPrice(new BigDecimal("150.00"));

            // subMap(low, false, high, false) — exclusive bounds
            var fired = index.evaluate(new BigDecimal("155.00"));

            assertThat(fired).isEmpty();
        }

        @Test
        void doesNotFire_whenPriceUnchanged() {
            index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
            index.setLastPrice(new BigDecimal("150.00"));

            var fired = index.evaluate(new BigDecimal("150.00"));

            assertThat(fired).isEmpty();
        }

        @Test
        void removedFromIndexAfterFiring() {
            index.addAlert(alert("c1", new BigDecimal("155.00"), Direction.CROSS));
            index.setLastPrice(new BigDecimal("150.00"));

            index.evaluate(new BigDecimal("160.00"));

            assertThat(index.size()).isZero();
        }
    }

    @Nested
    class MixedDirections {

        @Test
        void aboveAndBelowFireTogether() {
            index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
            index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));

            var fired = index.evaluate(new BigDecimal("150.00"));

            assertThat(fired).hasSize(2);
            assertThat(fired).extracting(AlertEntry::alertId)
                    .containsExactlyInAnyOrder("above1", "below1");
        }

        @Test
        void allThreeDirectionsFireTogether() {
            index.addAlert(alert("above1", new BigDecimal("100.00"), Direction.ABOVE));
            index.addAlert(alert("below1", new BigDecimal("200.00"), Direction.BELOW));
            index.addAlert(alert("cross1", new BigDecimal("155.00"), Direction.CROSS));
            index.setLastPrice(new BigDecimal("150.00"));

            var fired = index.evaluate(new BigDecimal("160.00"));

            assertThat(fired).hasSize(3);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void evaluate_emptyIndex_returnsEmpty() {
            var fired = index.evaluate(new BigDecimal("150.00"));
            assertThat(fired).isEmpty();
        }

        @Test
        void evaluate_updatesLastPrice() {
            index.evaluate(new BigDecimal("150.00"));
            assertThat(index.getLastPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        void verySmallPrice() {
            index.addAlert(alert("a1", new BigDecimal("0.01"), Direction.ABOVE));

            var fired = index.evaluate(new BigDecimal("0.01"));

            assertThat(fired).hasSize(1);
        }

        @Test
        void veryLargePrice() {
            index.addAlert(alert("a1", new BigDecimal("999999.99"), Direction.BELOW));

            var fired = index.evaluate(new BigDecimal("999999.99"));

            assertThat(fired).hasSize(1);
        }

        @Test
        void consecutiveEvaluations_onlyFireOnce() {
            index.addAlert(alert("a1", new BigDecimal("150.00"), Direction.ABOVE));

            var first = index.evaluate(new BigDecimal("155.00"));
            var second = index.evaluate(new BigDecimal("160.00"));

            assertThat(first).hasSize(1);
            assertThat(second).isEmpty();
        }
    }
}
