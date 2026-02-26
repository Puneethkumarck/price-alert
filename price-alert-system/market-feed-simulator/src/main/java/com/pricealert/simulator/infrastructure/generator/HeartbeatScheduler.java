package com.pricealert.simulator.infrastructure.generator;

import com.pricealert.simulator.application.websocket.SimulatorWebSocketHandler;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final SimulatorWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        try {
            var heartbeat = Map.of("type", "HEARTBEAT", "timestamp", Instant.now().toString());
            var json = objectMapper.writeValueAsString(heartbeat);
            webSocketHandler.broadcastHeartbeat(json);
        } catch (Exception e) {
            log.error("Failed to send heartbeat: {}", e.getMessage());
        }
    }
}
