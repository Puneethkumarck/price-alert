package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class CreateAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldCreateAlertAndPublishCreatedEvent() {
        given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

        var result = alertService.createAlert(USER_ID, "AAPL", new BigDecimal("150.00"), Direction.ABOVE, "test note");

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.thresholdPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.direction()).isEqualTo(Direction.ABOVE);
        assertThat(result.status()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(result.note()).isEqualTo("test note");
        assertThat(result.id()).isNotNull();

        var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.CREATED);
        assertThat(eventCaptor.getValue().alertId()).isEqualTo(result.id());
    }
}
