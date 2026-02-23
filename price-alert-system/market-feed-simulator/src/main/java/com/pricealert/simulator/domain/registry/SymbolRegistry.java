package com.pricealert.simulator.domain.registry;

import com.pricealert.simulator.application.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class SymbolRegistry {

    private final Map<String, BigDecimal> seedPrices;

    public SymbolRegistry(SimulatorProperties properties, ResourceLoader resourceLoader) {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        try {
            var resource = resourceLoader.getResource(properties.symbolsFile());
            try (var reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                var header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    var trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    var parts = trimmed.split(",");
                    if (parts.length >= 2) {
                        prices.put(parts[0].trim().toUpperCase(), new BigDecimal(parts[1].trim()));
                    }
                }
            }
            log.info("Loaded {} symbols from {}", prices.size(), properties.symbolsFile());
        } catch (Exception e) {
            log.error("Failed to load symbols file: {}", properties.symbolsFile(), e);
        }
        this.seedPrices = Collections.unmodifiableMap(prices);
    }

    public Set<String> allSymbols() {
        return seedPrices.keySet();
    }

    public BigDecimal seedPrice(String symbol) {
        return seedPrices.get(symbol);
    }

    public boolean isValid(String symbol) {
        return seedPrices.containsKey(symbol);
    }

    public int size() {
        return seedPrices.size();
    }
}
