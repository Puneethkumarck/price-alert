package com.pricealert.simulator.infrastructure.generator;

import com.pricealert.simulator.application.config.SimulatorProperties;
import com.pricealert.simulator.application.websocket.SimulatorWebSocketHandler;
import com.pricealert.simulator.domain.registry.SymbolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class TickGenerator {

    private final SimulatorProperties properties;
    private final SymbolRegistry symbolRegistry;
    private final SimulatorWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TickGenerator(SimulatorProperties properties,
                         SymbolRegistry symbolRegistry,
                         SimulatorWebSocketHandler webSocketHandler,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.symbolRegistry = symbolRegistry;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        for (var symbol : symbolRegistry.allSymbols()) {
            currentPrices.put(symbol, symbolRegistry.seedPrice(symbol));
        }

        log.info("Starting tick generation for {} symbols, interval={}ms, volatility={}",
                symbolRegistry.size(), properties.tickIntervalMs(), properties.volatility());

        for (var symbol : symbolRegistry.allSymbols()) {
            Thread.startVirtualThread(() -> generateTicksForSymbol(symbol));
        }
    }

    private void generateTicksForSymbol(String symbol) {
        var random = new Random();
        while (running.get()) {
            try {
                var price = currentPrices.get(symbol);
                var delta = price
                        .multiply(BigDecimal.valueOf(properties.volatility()))
                        .multiply(BigDecimal.valueOf(random.nextGaussian()));
                var newPrice = price.add(delta).max(BigDecimal.valueOf(0.01))
                        .setScale(2, RoundingMode.HALF_UP);

                currentPrices.put(symbol, newPrice);

                var spread = newPrice.multiply(BigDecimal.valueOf(0.0001))
                        .setScale(2, RoundingMode.HALF_UP)
                        .max(BigDecimal.valueOf(0.01));
                var bid = newPrice.subtract(spread);
                var ask = newPrice.add(spread);
                var seq = sequenceCounter.incrementAndGet();
                var volume = random.nextInt(1000, 10000);

                var tick = Map.of(
                        "type", "TICK",
                        "symbol", symbol,
                        "price", newPrice,
                        "bid", bid,
                        "ask", ask,
                        "volume", volume,
                        "timestamp", Instant.now().toString(),
                        "sequence", seq
                );

                var json = objectMapper.writeValueAsString(tick);
                webSocketHandler.broadcast(symbol, json);

                Thread.sleep(properties.tickIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error generating tick for {}: {}", symbol, e.getMessage());
            }
        }
    }

    public void stop() {
        running.set(false);
        log.info("Tick generation stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int activeSymbols() {
        return currentPrices.size();
    }
}
