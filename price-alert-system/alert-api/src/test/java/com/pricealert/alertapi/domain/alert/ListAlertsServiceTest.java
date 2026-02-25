package com.pricealert.alertapi.domain.alert;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class ListAlertsServiceTest extends AlertServiceBaseTest {

    @Test
    void shouldDelegateToRepository() {
        var pageable = PageRequest.of(0, 20);
        var alerts = List.of(buildAlert("alert_1", USER_ID));
        given(alertRepository.findByUserIdAndOptionalFilters(USER_ID, null, null, pageable))
                .willReturn(new PageImpl<>(alerts));

        var result = alertService.listAlerts(USER_ID, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        then(alertRepository).should().findByUserIdAndOptionalFilters(USER_ID, null, null, pageable);
    }
}
