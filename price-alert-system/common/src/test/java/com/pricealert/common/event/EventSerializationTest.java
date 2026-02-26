package com.pricealert.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.pricealert.common.json.JacksonConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EventSerializationTest {

    private final ObjectMapper mapper = JacksonConfig.createObjectMapper();

    @SneakyThrows
    @Test
    void marketTickRoundTrip() {
        // given
        var tick =
                new MarketTick(
                        "AAPL",
                        new BigDecimal("150.25"),
                        new BigDecimal("150.24"),
                        new BigDecimal("150.26"),
                        1200,
                        Instant.parse("2026-02-21T14:30:00.123Z"),
                        98237482L);

        // when
        var deserialized = mapper.readValue(mapper.writeValueAsString(tick), MarketTick.class);

        // then
        assertThat(deserialized)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(tick);
    }

    @SneakyThrows
    @Test
    void alertChangeRoundTrip() {
        // given
        var change =
                new AlertChange(
                        AlertChangeType.CREATED,
                        "alt_01HZ3X",
                        "usr_93fa",
                        "AAPL",
                        new BigDecimal("150.00"),
                        Direction.ABOVE,
                        Instant.parse("2026-02-21T14:30:00Z"));

        // when
        var json = mapper.writeValueAsString(change);
        var deserialized = mapper.readValue(json, AlertChange.class);

        // then
        assertThat(json).contains("\"event_type\":\"CREATED\"");
        assertThat(json).contains("\"alert_id\":\"alt_01HZ3X\"");
        assertThat(deserialized)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(change);
    }

    @SneakyThrows
    @Test
    void alertTriggerRoundTrip() {
        // given
        var trigger =
                new AlertTrigger(
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
                        LocalDate.of(2026, 2, 21));

        // when
        var deserialized = mapper.readValue(mapper.writeValueAsString(trigger), AlertTrigger.class);

        // then
        assertThat(deserialized)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(trigger);
    }

    @Test
    void alertChangeTypeIncludesReset() {
        assertThat(AlertChangeType.valueOf("RESET")).isEqualTo(AlertChangeType.RESET);
    }
}
