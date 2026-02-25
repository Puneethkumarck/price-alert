package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class DeleteAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldSoftDeleteAlertAndPublishDeletedEvent() {
        var existing = buildAlert("alert_1", USER_ID);
        given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
        given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

        alertService.deleteAlert("alert_1", USER_ID);

        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        then(alertRepository).should().save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().status()).isEqualTo(AlertStatus.DELETED);

        var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.DELETED);
    }
}
