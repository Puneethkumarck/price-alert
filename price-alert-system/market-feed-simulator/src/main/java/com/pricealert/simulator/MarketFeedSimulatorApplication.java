package com.pricealert.simulator;

import com.pricealert.simulator.application.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class MarketFeedSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketFeedSimulatorApplication.class, args);
    }
}
