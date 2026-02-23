package com.pricealert.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UlidGeneratorTest {

    @Test
    void generateReturns26CharString() {
        String ulid = UlidGenerator.generate();
        assertThat(ulid).hasSize(26);
    }

    @Test
    void generateUsesOnlyCrockfordBase32Characters() {
        String ulid = UlidGenerator.generate();
        assertThat(ulid).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }

    @Test
    void generatedUlidsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(UlidGenerator.generate());
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void generatedUlidsAreLexicographicallySorted() throws InterruptedException {
        String first = UlidGenerator.generate();
        Thread.sleep(2);
        String second = UlidGenerator.generate();
        assertThat(first.compareTo(second)).isLessThan(0);
    }
}
