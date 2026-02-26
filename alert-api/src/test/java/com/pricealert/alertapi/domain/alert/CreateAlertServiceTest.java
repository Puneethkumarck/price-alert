package com.pricealert.alertapi.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateAlertServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldCreateAlertAndPublishCreatedEvent() {
        // given
        given(alertRepository.save(any(Alert.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        var result =
                alertService.createAlert(
                        USER_ID, "AAPL", new BigDecimal("150.00"), Direction.ABOVE, "test note");

        // then
        assertThat(result.id()).isNotNull();
        var expected =
                Alert.builder()
                        .id(result.id())
                        .userId(USER_ID)
                        .symbol("AAPL")
                        .thresholdPrice(new BigDecimal("150.00"))
                        .direction(Direction.ABOVE)
                        .status(AlertStatus.ACTIVE)
                        .note("test note")
                        .createdAt(result.createdAt())
                        .updatedAt(result.updatedAt())
                        .build();
        assertThat(result)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);

        var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
        then(eventPublisher).should().publish(eventCaptor.capture());
        var expectedEvent =
                new AlertChange(
                        AlertChangeType.CREATED,
                        result.id(),
                        USER_ID,
                        "AAPL",
                        new BigDecimal("150.00"),
                        Direction.ABOVE,
                        eventCaptor.getValue().timestamp());
        assertThat(eventCaptor.getValue())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expectedEvent);
    }
}
