package com.pricealert.simulator.application.websocket;

import com.pricealert.simulator.domain.registry.SymbolRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class SimulatorWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SymbolRegistry symbolRegistry;
    private final Set<ClientSubscription> subscriptions = new CopyOnWriteArraySet<>();

    public SimulatorWebSocketHandler(ObjectMapper objectMapper, SymbolRegistry symbolRegistry) {
        this.objectMapper = objectMapper;
        this.symbolRegistry = symbolRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Client connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        var node = objectMapper.readTree(message.getPayload());
        var action = node.path("action").asText("");

        if ("subscribe".equalsIgnoreCase(action)) {
            var symbols = parseSymbols(node);
            var subscription = new ClientSubscription(session, symbols);
            subscriptions.add(subscription);
            log.info(
                    "Client {} subscribed to {} symbols",
                    session.getId(),
                    symbols.isEmpty() ? "ALL" : symbols.size());
        } else if ("unsubscribe".equalsIgnoreCase(action)) {
            subscriptions.removeIf(s -> s.session().equals(session));
            log.info("Client {} unsubscribed", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.removeIf(s -> s.session().equals(session));
        log.info("Client disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        subscriptions.removeIf(s -> s.session().equals(session));
        log.warn("Transport error for client {}: {}", session.getId(), exception.getMessage());
    }

    public void broadcast(String symbol, String tickJson) {
        var msg = new TextMessage(tickJson);
        for (var sub : subscriptions) {
            if (sub.isSubscribedTo(symbol) && sub.session().isOpen()) {
                sendSynchronized(sub.session(), msg);
            }
        }
    }

    public void broadcastHeartbeat(String heartbeatJson) {
        var msg = new TextMessage(heartbeatJson);
        for (var sub : subscriptions) {
            if (sub.session().isOpen()) {
                sendSynchronized(sub.session(), msg);
            }
        }
    }

    private void sendSynchronized(WebSocketSession session, TextMessage message) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to send to client {}: {}", session.getId(), e.getMessage());
        }
    }

    public int connectedClients() {
        return (int) subscriptions.stream().filter(s -> s.session().isOpen()).count();
    }

    private Set<String> parseSymbols(JsonNode node) {
        var symbolsNode = node.path("symbols");
        if (!symbolsNode.isArray() || symbolsNode.isEmpty()) {
            return Collections.emptySet();
        }
        if (symbolsNode.size() == 1 && "*".equals(symbolsNode.get(0).asText())) {
            return Collections.emptySet();
        }
        Set<String> symbols = ConcurrentHashMap.newKeySet();
        for (JsonNode s : symbolsNode) {
            var sym = s.asText("").toUpperCase();
            if (symbolRegistry.isValid(sym)) {
                symbols.add(sym);
            }
        }
        return symbols;
    }

    public record ClientSubscription(WebSocketSession session, Set<String> symbols) {
        public boolean isSubscribedTo(String symbol) {
            return symbols.isEmpty() || symbols.contains(symbol);
        }
    }
}
