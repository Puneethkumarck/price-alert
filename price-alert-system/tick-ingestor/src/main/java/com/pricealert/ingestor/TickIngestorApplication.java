package com.pricealert.ingestor;

import com.pricealert.ingestor.application.config.IngestorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IngestorProperties.class)
public class TickIngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TickIngestorApplication.class, args);
    }
}
