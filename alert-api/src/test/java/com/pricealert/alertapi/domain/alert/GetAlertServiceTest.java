package com.pricealert.alertapi.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.pricealert.alertapi.domain.exceptions.AlertNotFoundException;
import com.pricealert.alertapi.domain.exceptions.AlertNotOwnedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldReturnAlertWhenFoundAndOwnedByUser() {
        // given
        var alert = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(alert));

        // when
        var result = alertService.getAlert("alert_1", USER_ID);

        // then
        assertThat(result).isEqualTo(alert);
    }

    @Test
    void shouldThrowWhenAlertNotFound() {
        // given
        given(alertRepository.findById("missing")).willReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> alertService.getAlert("missing", USER_ID))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void shouldThrowWhenAlertNotOwnedByUser() {
        // given
        var alert = buildAlert("alert_1", OTHER_USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(alert));

        // when/then
        assertThatThrownBy(() -> alertService.getAlert("alert_1", USER_ID))
                .isInstanceOf(AlertNotOwnedException.class);
    }
}
