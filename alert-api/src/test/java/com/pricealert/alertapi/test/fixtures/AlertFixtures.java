package com.pricealert.alertapi.test.fixtures;

import com.pricealert.alertapi.domain.alert.Alert;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AlertFixtures {

    public static final String SOME_USER_ID = "user_test_001";
    public static final String SOME_OTHER_USER_ID = "user_test_002";
    public static final String SOME_ALERT_ID = "alert_test_001";
    public static final String SOME_SYMBOL = "AAPL";
    public static final BigDecimal SOME_THRESHOLD_PRICE = new BigDecimal("150.00");
    public static final String JWT_SECRET = "test-secret-key-for-integration-tests";

    public static Alert.AlertBuilder activeAlertBuilder() {
        return Alert.builder()
                .id(SOME_ALERT_ID)
                .userId(SOME_USER_ID)
                .symbol(SOME_SYMBOL)
                .thresholdPrice(SOME_THRESHOLD_PRICE)
                .direction(Direction.ABOVE)
                .status(AlertStatus.ACTIVE)
                .note("test alert")
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    public static Alert.AlertBuilder triggeredAlertBuilder() {
        return activeAlertBuilder()
                .status(AlertStatus.TRIGGERED_TODAY)
                .lastTriggeredAt(Instant.now())
                .lastTriggerPrice(new BigDecimal("155.50"));
    }
}
