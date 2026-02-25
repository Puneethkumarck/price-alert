package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class UpdateAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldUpdateAlertFieldsAndPublishUpdatedEvent() {
        var existing = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
        given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = alertService.updateAlert("alert_1", USER_ID, new BigDecimal("200.00"), Direction.BELOW, "updated");

        assertThat(result.thresholdPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.direction()).isEqualTo(Direction.BELOW);
        assertThat(result.note()).isEqualTo("updated");

        var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.UPDATED);
    }

    @Test
    void shouldKeepExistingFieldsWhenUpdateFieldsAreNull() {
        var existing = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
        given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = alertService.updateAlert("alert_1", USER_ID, null, null, null);

        assertThat(result.thresholdPrice()).isEqualByComparingTo(existing.thresholdPrice());
        assertThat(result.direction()).isEqualTo(existing.direction());
        assertThat(result.note()).isEqualTo(existing.note());
    }
}
