package com.pricealert.alertapi;

import com.pricealert.alertapi.infrastructure.db.alert.AlertEntity;
import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.common.event.AlertStatus;
import com.pricealert.common.event.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertControllerIntegrationTest extends BaseIntegrationTest {

    private static final String USER_ID = "user_test_001";
    private static final String OTHER_USER_ID = "user_test_002";
    private static final String ALERTS_PATH = "/api/v1/alerts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertJpaRepository alertJpaRepository;

    private String validToken;
    private String otherUserToken;

    @BeforeEach
    void setUp() {
        alertJpaRepository.deleteAll();
        validToken = JwtTestUtil.generateToken(USER_ID, JWT_SECRET);
        otherUserToken = JwtTestUtil.generateToken(OTHER_USER_ID, JWT_SECRET);
    }

    @Nested
    class CreateAlert {

        @Test
        void shouldCreateAlertAndReturn201() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "AAPL",
                        "thresholdPrice": 150.50,
                        "direction": "ABOVE",
                        "note": "Buy signal"
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.userId", is(USER_ID)))
                    .andExpect(jsonPath("$.symbol", is("AAPL")))
                    .andExpect(jsonPath("$.thresholdPrice").value(150.50))
                    .andExpect(jsonPath("$.direction", is("ABOVE")))
                    .andExpect(jsonPath("$.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.note", is("Buy signal")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andExpect(jsonPath("$.updatedAt", notNullValue()));

            // verify persisted
            assertThat(alertJpaRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectInvalidSymbol() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "invalid123",
                        "thresholdPrice": 150.50,
                        "direction": "ABOVE"
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Bad Request")));
        }

        @Test
        void shouldRejectMissingThresholdPrice() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "AAPL",
                        "direction": "ABOVE"
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectMissingDirection() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "AAPL",
                        "thresholdPrice": 150.50
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectZeroThresholdPrice() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "AAPL",
                        "thresholdPrice": 0,
                        "direction": "ABOVE"
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldCreateAlertWithNullNote() throws Exception {
            // given
            var requestBody = """
                    {
                        "symbol": "TSLA",
                        "thresholdPrice": 200.00,
                        "direction": "BELOW"
                    }
                    """;

            // when/then
            mockMvc.perform(post(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.symbol", is("TSLA")))
                    .andExpect(jsonPath("$.direction", is("BELOW")));
        }
    }

    @Nested
    class GetAlert {

        @Test
        void shouldReturnAlertById() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

            // when/then
            mockMvc.perform(get(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(entity.getId())))
                    .andExpect(jsonPath("$.symbol", is("AAPL")))
                    .andExpect(jsonPath("$.userId", is(USER_ID)));
        }

        @Test
        void shouldReturn404ForNonExistentAlert() throws Exception {
            // when/then
            mockMvc.perform(get(ALERTS_PATH + "/nonexistent_id")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Alert Not Found")));
        }

        @Test
        void shouldReturn404WhenAlertBelongsToAnotherUser() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

            // when/then — requesting with USER_ID's token for OTHER_USER_ID's alert
            mockMvc.perform(get(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ListAlerts {

        @Test
        void shouldReturnPaginatedAlerts() throws Exception {
            // given
            createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);
            createAlertEntity("GOOG", OTHER_USER_ID, AlertStatus.ACTIVE);

            // when/then — should only see own alerts
            mockMvc.perform(get(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements", is(2)));
        }

        @Test
        void shouldFilterByStatus() throws Exception {
            // given
            createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            createAlertEntity("TSLA", USER_ID, AlertStatus.TRIGGERED_TODAY);

            // when/then
            mockMvc.perform(get(ALERTS_PATH + "?status=ACTIVE")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].symbol", is("AAPL")));
        }

        @Test
        void shouldFilterBySymbol() throws Exception {
            // given
            createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);

            // when/then
            mockMvc.perform(get(ALERTS_PATH + "?symbol=TSLA")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].symbol", is("TSLA")));
        }

        @Test
        void shouldReturnEmptyPageForNoResults() throws Exception {
            // when/then
            mockMvc.perform(get(ALERTS_PATH)
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        void shouldFilterByBothStatusAndSymbol() throws Exception {
            // given
            createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            createAlertEntity("AAPL", USER_ID, AlertStatus.TRIGGERED_TODAY);
            createAlertEntity("TSLA", USER_ID, AlertStatus.ACTIVE);

            // when/then
            mockMvc.perform(get(ALERTS_PATH + "?status=ACTIVE&symbol=AAPL")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].symbol", is("AAPL")))
                    .andExpect(jsonPath("$.content[0].status", is("ACTIVE")));
        }
    }

    @Nested
    class UpdateAlert {

        @Test
        void shouldUpdateThresholdPrice() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            var requestBody = """
                    {
                        "thresholdPrice": 175.00
                    }
                    """;

            // when/then
            mockMvc.perform(patch(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thresholdPrice").value(175.00))
                    .andExpect(jsonPath("$.direction", is("ABOVE")));

            // verify persisted
            var updated = alertJpaRepository.findById(entity.getId()).orElseThrow();
            assertThat(updated.getThresholdPrice()).isEqualByComparingTo(new BigDecimal("175.00"));
        }

        @Test
        void shouldUpdateDirection() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            var requestBody = """
                    {
                        "direction": "BELOW"
                    }
                    """;

            // when/then
            mockMvc.perform(patch(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.direction", is("BELOW")));
        }

        @Test
        void shouldUpdateNote() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);
            var requestBody = """
                    {
                        "note": "Updated note"
                    }
                    """;

            // when/then
            mockMvc.perform(patch(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.note", is("Updated note")));
        }

        @Test
        void shouldReturn404WhenUpdatingNonExistentAlert() throws Exception {
            // given
            var requestBody = """
                    {
                        "thresholdPrice": 175.00
                    }
                    """;

            // when/then
            mockMvc.perform(patch(ALERTS_PATH + "/nonexistent_id")
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404WhenUpdatingOtherUsersAlert() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);
            var requestBody = """
                    {
                        "thresholdPrice": 175.00
                    }
                    """;

            // when/then
            mockMvc.perform(patch(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DeleteAlert {

        @Test
        void shouldSoftDeleteAlertAndReturn204() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", USER_ID, AlertStatus.ACTIVE);

            // when/then
            mockMvc.perform(delete(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isNoContent());

            // verify soft-deleted
            var deleted = alertJpaRepository.findById(entity.getId()).orElseThrow();
            assertThat(deleted.getStatus()).isEqualTo(AlertStatus.DELETED);
        }

        @Test
        void shouldReturn404WhenDeletingNonExistentAlert() throws Exception {
            // when/then
            mockMvc.perform(delete(ALERTS_PATH + "/nonexistent_id")
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404WhenDeletingOtherUsersAlert() throws Exception {
            // given
            var entity = createAlertEntity("AAPL", OTHER_USER_ID, AlertStatus.ACTIVE);

            // when/then
            mockMvc.perform(delete(ALERTS_PATH + "/" + entity.getId())
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Authentication {

        @Test
        void shouldReturn403WhenNoTokenProvided() throws Exception {
            // when/then
            mockMvc.perform(get(ALERTS_PATH))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn403WhenInvalidTokenProvided() throws Exception {
            // when/then
            mockMvc.perform(get(ALERTS_PATH)
                            .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldAllowActuatorWithoutToken() throws Exception {
            // when/then
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    private AlertEntity createAlertEntity(String symbol, String userId, AlertStatus status) {
        var now = Instant.now();
        var entity = AlertEntity.builder()
                .id("alt_" + System.nanoTime())
                .userId(userId)
                .symbol(symbol)
                .thresholdPrice(new BigDecimal("150.00"))
                .direction(Direction.ABOVE)
                .status(status)
                .note("Test alert")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return alertJpaRepository.save(entity);
    }
}
