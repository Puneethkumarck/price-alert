package com.pricealert.alertapi.controller;

import com.pricealert.alertapi.BaseIntegrationTest;
import com.pricealert.alertapi.JwtTestUtil;
import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

public abstract class AlertControllerBaseTest extends BaseIntegrationTest {

    static final String USER_ID = "user_test_001";
    static final String OTHER_USER_ID = "user_test_002";
    static final String ALERTS_PATH = "/api/v1/alerts";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AlertJpaRepository alertJpaRepository;

    String validToken;
    String otherUserToken;

    @BeforeEach
    void setUpTokens() {
        validToken = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        otherUserToken = JwtTestUtil.generateToken(OTHER_USER_ID, JWT_SECRET);
    }

    AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        return alertJpaRepository.save(AlertEntity.builder()
                .id("alt_" + System.nanoTime())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(status)
                .note("Test alert")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
