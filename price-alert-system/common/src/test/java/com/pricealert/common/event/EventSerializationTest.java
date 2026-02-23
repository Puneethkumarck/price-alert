package com.pricealert.common.event;

import tools.jackson.databind.ObjectMapper;
import com.pricealert.common.json.JacksonConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {

    private final ObjectMapper mapper = JacksonConfig.createObjectMapper();

    @Test
    void marketTickRoundTrip() throws Exception {
        var tick = new MarketTick(
                "AAPL",
                new BigDecimal("150.25"),
                new BigDecimal("150.24"),
                new BigDecimal("150.26"),
                1200,
                Instant.parse("2026-02-21T14:30:00.123Z"),
                98237482L
        );

        String json = mapper.writeValueAsString(tick);
        var deserialized = mapper.readValue(json, MarketTick.class);

        assertThat(deserialized.symbol()).isEqualTo("AAPL");
        assertThat(deserialized.price()).isEqualByComparingTo("150.25");
        assertThat(deserialized.timestamp()).isEqualTo(tick.timestamp());
        assertThat(deserialized.sequence()).isEqualTo(98237482L);
    }

    @Test
    void alertChangeRoundTrip() throws Exception {
        var change = new AlertChange(
                AlertChangeType.CREATED,
                "alt_01HZ3X",
                "usr_93fa",
                "AAPL",
                new BigDecimal("150.00"),
                Direction.ABOVE,
                Instant.parse("2026-02-21T14:30:00Z")
        );

        String json = mapper.writeValueAsString(change);
        assertThat(json).contains("\"event_type\":\"CREATED\"");
        assertThat(json).contains("\"alert_id\":\"alt_01HZ3X\"");

        var deserialized = mapper.readValue(json, AlertChange.class);
        assertThat(deserialized.eventType()).isEqualTo(AlertChangeType.CREATED);
        assertThat(deserialized.alertId()).isEqualTo("alt_01HZ3X");
        assertThat(deserialized.thresholdPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void alertTriggerRoundTrip() throws Exception {
        var trigger = new AlertTrigger(
                "trg_01HZ4Y",
                "alt_01HZ3X",
                "usr_93fa",
                "AAPL",
                new BigDecimal("150.00"),
                new BigDecimal("150.25"),
                Direction.ABOVE,
                "Buy signal",
                Instant.parse("2026-02-21T14:30:00.123Z"),
                Instant.parse("2026-02-21T14:30:00.200Z"),
                LocalDate.of(2026, 2, 21)
        );

        String json = mapper.writeValueAsString(trigger);
        var deserialized = mapper.readValue(json, AlertTrigger.class);

        assertThat(deserialized.triggerId()).isEqualTo("trg_01HZ4Y");
        assertThat(deserialized.triggerPrice()).isEqualByComparingTo("150.25");
        assertThat(deserialized.tradingDate()).isEqualTo(LocalDate.of(2026, 2, 21));
        assertThat(deserialized.note()).isEqualTo("Buy signal");
    }

    @Test
    void alertChangeTypeIncludesReset() {
        assertThat(AlertChangeType.valueOf("RESET")).isEqualTo(AlertChangeType.RESET);
    }
}
