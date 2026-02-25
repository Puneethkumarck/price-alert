package com.pricealert.alertapi.domain.alert;

import com.pricealert.alertapi.domain.exceptions.AlertNotFoundException;
import com.pricealert.alertapi.domain.exceptions.AlertNotOwnedException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class GetAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldReturnAlertWhenFoundAndOwnedByUser() {
        var alert = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(alert));

        var result = alertService.getAlert("alert_1", USER_ID);

        assertThat(result).isEqualTo(alert);
    }

    @Test
    void shouldThrowWhenAlertNotFound() {
        given(alertRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.getAlert("missing", USER_ID))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void shouldThrowWhenAlertNotOwnedByUser() {
        var alert = buildAlert("alert_1", OTHER_USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.getAlert("alert_1", USER_ID))
                .isInstanceOf(AlertNotOwnedException.class);
    }
}
