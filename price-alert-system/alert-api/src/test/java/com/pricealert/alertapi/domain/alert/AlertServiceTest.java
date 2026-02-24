package com.pricealert.alertapi.domain.alert;

import com.pricealert.alertapi.domain.exceptions.AlertNotFoundException;
import com.pricealert.alertapi.domain.exceptions.AlertNotOwnedException;
import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.AlertChangeType;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final String USER_ID = "user_001";
    private static final String OTHER_USER_ID = "user_002";

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertEventPublisher eventPublisher;

    @InjectMocks
    private AlertService alertService;

    @Nested
    class CreateAlert {

        @Test
        void shouldCreateAlertAndPublishCreatedEvent() {
            // given
            given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            var result = alertService.createAlert(USER_ID, "AAPL", new BigDecimal("150.00"), Direction.ABOVE, "test note");

            // then
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

    @Nested
    class GetAlert {

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

    @Nested
    class UpdateAlert {

        @Test
        void shouldUpdateAlertFieldsAndPublishUpdatedEvent() {
            // given
            var existing = buildAlert("alert_1", USER_ID);
            given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
            given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            var result = alertService.updateAlert("alert_1", USER_ID, new BigDecimal("200.00"), Direction.BELOW, "updated");

            // then
            assertThat(result.thresholdPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(result.direction()).isEqualTo(Direction.BELOW);
            assertThat(result.note()).isEqualTo("updated");

            var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
            then(eventPublisher).should().publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.UPDATED);
        }

        @Test
        void shouldKeepExistingFieldsWhenUpdateFieldsAreNull() {
            // given
            var existing = buildAlert("alert_1", USER_ID);
            given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
            given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            var result = alertService.updateAlert("alert_1", USER_ID, null, null, null);

            // then
            assertThat(result.thresholdPrice()).isEqualByComparingTo(existing.thresholdPrice());
            assertThat(result.direction()).isEqualTo(existing.direction());
            assertThat(result.note()).isEqualTo(existing.note());
        }
    }

    @Nested
    class DeleteAlert {

        @Test
        void shouldSoftDeleteAlertAndPublishDeletedEvent() {
            // given
            var existing = buildAlert("alert_1", USER_ID);
            given(alertRepository.findById("alert_1")).willReturn(Optional.of(existing));
            given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            alertService.deleteAlert("alert_1", USER_ID);

            // then
            var alertCaptor = ArgumentCaptor.forClass(Alert.class);
            then(alertRepository).should().save(alertCaptor.capture());
            assertThat(alertCaptor.getValue().status()).isEqualTo(AlertStatus.DELETED);

            var eventCaptor = ArgumentCaptor.forClass(AlertChange.class);
            then(eventPublisher).should().publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue().eventType()).isEqualTo(AlertChangeType.DELETED);
        }
    }

    @Nested
    class ListAlerts {

        @Test
        void shouldDelegateToRepository() {
            // given
            var pageable = PageRequest.of(0, 20);
            var alerts = List.of(buildAlert("alert_1", USER_ID));
            given(alertRepository.findByUserIdAndOptionalFilters(USER_ID, null, null, pageable))
                    .willReturn(new PageImpl<>(alerts));

            // when
            var result = alertService.listAlerts(USER_ID, null, null, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            then(alertRepository).should().findByUserIdAndOptionalFilters(USER_ID, null, null, pageable);
        }
    }

    private Alert buildAlert(String id, String userId) {
        return Alert.builder()
                .id(id)
                .userId(userId)
                .symbol("AAPL")
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(AlertStatus.ACTIVE)
                .note("test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
