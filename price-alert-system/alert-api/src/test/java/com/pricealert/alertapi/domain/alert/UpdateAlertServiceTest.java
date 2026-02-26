package com.pricealert.alertapi.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldUpdateAlertFieldsAndPublishUpdatedEvent() {
        // given
        var existing = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
        given(alertRepository.save(any(Alert.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        var result =
                alertService.updateAlert(
                        "alert_1", USER_ID, new BigDecimal("200.00"), Direction.BELOW, "updated");

        // then
        var expected =
                existing.toBuilder()
                        .thresholdPrice(new BigDecimal("200.00"))
                        .direction(Direction.BELOW)
                        .note("updated")
                        .updatedAt(result.updatedAt())
                        .build();
        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);

        var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.UPDATED);
    }

    @Test
    void shouldKeepExistingFieldsWhenUpdateFieldsAreNull() {
        // given
        var existing = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
        given(alertRepository.save(any(Alert.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = alertService.updateAlert("alert_1", USER_ID, null, null, null);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFields("updatedAt")
                .isEqualTo(existing);
    }
}
