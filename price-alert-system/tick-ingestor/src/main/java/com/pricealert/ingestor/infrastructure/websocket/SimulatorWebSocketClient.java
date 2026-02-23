package com.pricealert.ingestor.infrastructure.websocket;

import com.pricealert.ingestor.application.config.IngestorProperties;
import com.pricealert.ingestor.infrastructure.kafka.TickKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SimulatorWebSocketClient extends TextWebSocketHandler {

    private final IngestorProperties properties;
    private final TickKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SimulatorWebSocketClient(IngestorProperties properties, TickKafkaProducer kafkaProducer) {
        this.properties = properties;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = JsonMapper.builder().build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        Thread.startVirtualThread(this::connectWithRetry);
    }

    private void connectWithRetry() {
        var delay = properties.reconnect().initialDelayMs();
        while (running.get()) {
            try {
                log.info("Connecting to simulator at {}", properties.simulatorUrl());
                var client = new StandardWebSocketClient();
                var session = client.execute(this, properties.simulatorUrl()).get();
                log.info("Connected to simulator, session={}", session.getId());

                var subscribeMsg = Map.of(
                        "action", "subscribe",
                        "symbols", properties.subscribeSymbols()
                );
                var json = objectMapper.writeValueAsString(subscribeMsg);
                session.sendMessage(new TextMessage(json));
                log.info("Subscribed to symbols: {}", properties.subscribeSymbols());

                delay = properties.reconnect().initialDelayMs();

                while (session.isOpen() && running.get()) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.warn("Connection failed: {}. Retrying in {}ms", e.getMessage(), delay);
            }

            if (!running.get()) break;

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            delay = Math.min(delay * properties.reconnect().multiplier(), properties.reconnect().maxDelayMs());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            kafkaProducer.send(message.getPayload());
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("Simulator connection closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error: {}", exception.getMessage());
    }

    public void stop() {
        running.set(false);
    }
}
