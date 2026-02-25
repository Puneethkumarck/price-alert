package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
public abstract class AlertServiceBaseTest {

    static final String USER_ID = "user_001";
    static final String OTHER_USER_ID = "user_002";

    @Mock
    AlertRepository alertRepository;

    @Mock
    AlertEventPublisher eventPublisher;

    @InjectMocks
    AlertService alertService;

    Alert buildAlert(String id, String userId) {
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
